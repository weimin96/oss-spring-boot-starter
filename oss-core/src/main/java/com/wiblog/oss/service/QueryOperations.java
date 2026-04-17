package com.wiblog.oss.service;

import com.wiblog.oss.bean.*;
import com.wiblog.oss.bean.BucketInfo;
import com.wiblog.oss.util.Util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Publisher;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 查询操作类（核心逻辑，不依赖任何 Servlet API）
 * <p>
 * previewObject 相关的 HTTP 响应写入逻辑抽象为 {@link OssPreviewContext}，
 * 由各 Spring Boot 版本 starter 实现具体的 Servlet 适配。
 *
 * @author panwm
 */
@Slf4j
public class QueryOperations extends Operations {

    /**
     * 预览/下载时的 IO 缓冲区大小 4KB
     */
    private static final int BUFFER_SIZE = 4 * 1024;

    /**
     * 列举对象时每页最大数量
     */
    private static final int LIST_MAX_KEYS = 1000;

    public QueryOperations(OssProperties ossProperties, S3AsyncClient client,
                           S3TransferManager transferManager) {
        super(ossProperties, client, transferManager);
    }

    /**
     * 返回当前查询操作绑定的 OSS 配置。
     *
     * @return OSS 配置
     */
    public OssProperties getOssProperties() {
        return ossProperties;
    }

    // ----------------------------------------------------------------
    // 连接 / Bucket 检测
    // ----------------------------------------------------------------

    /**
     * 测试默认 Bucket 是否可访问。
     *
     * @return 可访问返回 {@code true}
     */
    public boolean testConnect() {
        return testConnectForBucket();
    }

    /**
     * 测试指定 Bucket 是否可访问。
     *
     * @param bucketName Bucket 名称
     * @return 可访问返回 {@code true}
     */
    public boolean testConnectForBucket(String bucketName) {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build()).join();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 使用默认配置里的 Bucket 执行连通性测试。
     *
     * @return 可访问返回 {@code true}
     */
    public boolean testConnectForBucket() {
        return testConnectForBucket(ossProperties.getBucketName());
    }

    /**
     * 列举当前凭证可见的全部 Bucket。
     *
     * @return Bucket 列表
     */
    public List<BucketInfo> getAllBuckets() {
        return client.listBuckets().join().buckets().stream()
                .map(bucket -> BucketInfo.builder()
                        .name(bucket.name())
                        .creationDate(bucket.creationDate() == null ? null : Date.from(bucket.creationDate()))
                        .build())
                .collect(Collectors.toList());
    }

    // ----------------------------------------------------------------
    // 对象列表查询
    // ----------------------------------------------------------------

    /**
     * 列举默认 Bucket 某个前缀下的所有对象。
     *
     * @param path 查询前缀
     * @return 对象信息列表
     */
    public List<ObjectInfo> listObjects(String path) {
        return listObjects(ossProperties.getBucketName(), path);
    }

    /**
     * 列举指定 Bucket 某个前缀下的所有对象。
     *
     * @param bucketName Bucket 名称
     * @param path       查询前缀
     * @return 对象信息列表
     */
    public List<ObjectInfo> listObjects(String bucketName, String path) {
        return listObject(bucketName, path, null).stream()
                .map(e -> ObjectInfo.builder()
                        .uri(e.key())
                        .url(getDomain() + e.key())
                        .name(Util.getFilename(e.key()))
                        .size(e.size())
                        .ext(Util.getExtension(e.key()))
                        .uploadTime(Date.from(e.lastModified()))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 列举默认 Bucket 下的原始 S3 对象列表。
     *
     * @param path 查询前缀
     * @return 原始对象列表
     */
    public List<S3Object> listObject(String path) {
        return listObject(ossProperties.getBucketName(), path, null);
    }

    /**
     * 列举指定 Bucket 下的原始 S3 对象列表。
     *
     * @param bucketName Bucket 名称
     * @param path       查询前缀
     * @return 原始对象列表
     */
    public List<S3Object> listObject(String bucketName, String path) {
        return listObject(bucketName, path, null);
    }

    /**
     * 列举指定 Bucket 下的对象，并可按关键字二次过滤。
     *
     * <p>S3 本身只支持按前缀筛选，不支持任意关键字查询，
     * 因此这里先按前缀分页拉取，再在客户端侧按关键字过滤。</p>
     *
     * @param bucketName Bucket 名称
     * @param path       查询前缀
     * @param keyword    关键字；为空时不过滤
     * @return 原始对象列表
     */
    public List<S3Object> listObject(String bucketName, String path, String keyword) {
        List<S3Object> list = new ArrayList<>();
        String prefix = Util.formatPath(path);

        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName).maxKeys(LIST_MAX_KEYS).prefix(prefix).build();

        ListObjectsV2Publisher publisher = client.listObjectsV2Paginator(request);
        publisher.subscribe(response -> {
            if (Util.isBlank(keyword)) {
                list.addAll(response.contents());
            } else {
                response.contents().stream()
                        .filter(e -> e.key().contains(keyword))
                        .forEach(list::add);
            }
        }).join();
        return list;
    }

    // ----------------------------------------------------------------
    // 懒加载列表
    // ----------------------------------------------------------------

    /**
     * 查询默认 Bucket 的懒加载分页列表。
     *
     * @param path              查询前缀
     * @param maxKeys           单次返回上限
     * @param continuationToken 分页游标
     * @return 懒加载分页结果
     */
    public LazyDataList<ObjectInfo> lazyList(String path, int maxKeys, String continuationToken) {
        return lazyList(ossProperties.getBucketName(), path, maxKeys, continuationToken);
    }

    /**
     * 查询指定 Bucket 的懒加载分页列表。
     *
     * <p>第一页会额外补充下一层级目录节点，
     * 目的是让调用方在“分页文件 + 目录结构”并存的界面里一次拿到可展示数据。</p>
     *
     * @param bucketName        Bucket 名称
     * @param path              查询前缀
     * @param maxKeys           单次返回上限
     * @param continuationToken 分页游标
     * @return 懒加载分页结果
     */
    public LazyDataList<ObjectInfo> lazyList(String bucketName, String path,
                                             int maxKeys, String continuationToken) {
        if (maxKeys <= 0) {
            maxKeys = 1000;
        }
        LazyDataList<ObjectInfo> resultList = new LazyDataList<>();

        ListObjectsV2Request.Builder builder = ListObjectsV2Request.builder()
                .bucket(bucketName).prefix(Util.formatPath(path))
                .maxKeys(maxKeys).delimiter("/");

        if (StringUtils.hasText(continuationToken)) {
            builder.continuationToken(continuationToken);
        } else {
            resultList.addAll(listNextLevelFolder(bucketName, path));
        }

        ListObjectsV2Response response = client.listObjectsV2(builder.build()).join();
        response.contents().stream()
                .filter(e -> e.size() > 0)
                .map(e -> buildObjectInfo(e.key(), Date.from(e.lastModified()), e.size()))
                .forEach(resultList::add);

        resultList.setMaxKeys(maxKeys);
        resultList.setContinuationToken(response.nextContinuationToken());
        return resultList;
    }

    // ----------------------------------------------------------------
    // 树形结构查询
    // ----------------------------------------------------------------

    /**
     * 列举默认 Bucket 指定前缀下一层级的文件和目录。
     *
     * @param path 查询前缀
     * @return 下一层级节点列表
     */
    public List<ObjectTreeNode> listNextLevel(String path) {
        return listNextLevel(ossProperties.getBucketName(), path);
    }

    /**
     * 列举指定 Bucket 指定前缀下一层级的文件和目录。
     *
     * @param bucketName Bucket 名称
     * @param path       查询前缀
     * @return 下一层级节点列表
     */
    public List<ObjectTreeNode> listNextLevel(String bucketName, String path) {
        List<ObjectTreeNode> resultList = new ArrayList<>();
        String prefix = Util.formatPath(path);

        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName).prefix(prefix)
                .maxKeys(LIST_MAX_KEYS).delimiter("/").build();

        Set<String> seen = new HashSet<>(64);
        client.listObjectsV2Paginator(request).subscribe(response -> {
            response.contents().stream()
                    .filter(e -> e.size() > 0)
                    .map(this::buildTreeNode)
                    .forEach(resultList::add);
            response.commonPrefixes().stream()
                    .map(CommonPrefix::prefix)
                    .filter(seen::add)
                    .map(this::buildTreeNode)
                    .forEach(resultList::add);
        }).join();
        return resultList;
    }

    /**
     * 获取默认 Bucket 的纯文件夹树。
     *
     * @param path 查询前缀
     * @return 文件夹树
     */
    public List<ObjectTreeNode> getFolderTreeList(String path) {
        return getFolderTreeList(ossProperties.getBucketName(), path);
    }

    /**
     * 获取指定 Bucket 的纯文件夹树。
     *
     * @param bucketName Bucket 名称
     * @param path       查询前缀
     * @return 文件夹树
     */
    public List<ObjectTreeNode> getFolderTreeList(String bucketName, String path) {
        String prefix = Util.formatPath(path);
        List<S3Object> list = new ArrayList<>();

        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName).maxKeys(LIST_MAX_KEYS).prefix(prefix).build();

        client.listObjectsV2Paginator(request)
                .subscribe(r -> list.addAll(r.contents())).join();

        ObjectTreeNode root = buildFolderTree(list, prefix);
        return root.getChildren() == null ? Collections.emptyList() : root.getChildren();
    }

    /**
     * 列举默认 Bucket 指定路径下的下一层目录，并转换成 {@link ObjectInfo} 结构。
     *
     * @param path 查询前缀
     * @return 目录信息列表
     */
    public List<ObjectInfo> listNextLevelFolder(String path) {
        return listNextLevelFolder(ossProperties.getBucketName(), path);
    }

    /**
     * 列举指定 Bucket 指定路径下的下一层目录，并转换成 {@link ObjectInfo} 结构。
     *
     * @param bucketName Bucket 名称
     * @param path       查询前缀
     * @return 目录信息列表
     */
    public List<ObjectInfo> listNextLevelFolder(String bucketName, String path) {
        List<ObjectInfo> resultList = new ArrayList<>();
        String prefix = Util.formatPath(path);
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName).prefix(prefix).delimiter("/").build();
        Set<String> seen = new HashSet<>(64);
        client.listObjectsV2Paginator(request).subscribe(response ->
                response.commonPrefixes().stream()
                        .map(CommonPrefix::prefix)
                        .filter(seen::add)
                        .map(this::buildTreeNode)
                        .map(node -> ObjectInfo.builder()
                                .uri(node.getUri()).url(node.getUrl()).name(node.getName()).build())
                        .forEach(resultList::add)
        ).join();
        return resultList;
    }

    // ----------------------------------------------------------------
    // 文件存在性 & 元数据
    // ----------------------------------------------------------------

    /**
     * 检查默认 Bucket 中对象是否存在。
     *
     * @param objectName 对象 key
     * @return 存在返回 {@code true}
     */
    public boolean checkExist(String objectName) {
        return checkExist(ossProperties.getBucketName(), objectName);
    }

    /**
     * 检查指定 Bucket 中对象是否存在。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象 key
     * @return 存在返回 {@code true}
     */
    public boolean checkExist(String bucketName, String objectName) {
        try {
            client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName).key(objectName).build()).join();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 查询默认 Bucket 中对象的元数据。
     *
     * @param objectName 对象 key
     * @return 对象信息
     */
    public ObjectInfo getObjectInfo(String objectName) {
        return getObjectInfo(ossProperties.getBucketName(), objectName);
    }

    /**
     * 查询指定 Bucket 中对象的元数据。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象 key
     * @return 对象信息
     */
    public ObjectInfo getObjectInfo(String bucketName, String objectName) {
        HeadObjectRequest req = HeadObjectRequest.builder()
                .bucket(bucketName).key(objectName).build();
        HeadObjectResponse response = handleRequest(() -> client.headObject(req));
        return buildObjectInfo(objectName, response);
    }

    // ----------------------------------------------------------------
    // 内容读取
    // ----------------------------------------------------------------

    /**
     * 读取默认 Bucket 中对象的文本内容。
     *
     * @param objectName 对象 key
     * @return UTF-8 文本内容；对象不存在时返回 {@code null}
     */
    public String getContent(String objectName) {
        return getContent(ossProperties.getBucketName(), objectName);
    }

    /**
     * 读取指定 Bucket 中对象的文本内容。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象 key
     * @return UTF-8 文本内容；对象不存在时返回 {@code null}
     */
    public String getContent(String bucketName, String objectName) {
        try {
            return client.getObject(buildGetRequest(bucketName, objectName),
                            AsyncResponseTransformer.toBytes())
                    .thenApply(rb -> StandardCharsets.UTF_8.decode(rb.asByteBuffer()).toString())
                    .join();
        } catch (NoSuchKeyException e) {
            log.error("File not found: [{}]", objectName);
            return null;
        }
    }

    /**
     * 获取默认 Bucket 中对象的输入流。
     *
     * @param objectName 对象 key
     * @return 对象输入流
     */
    public InputStream getInputStream(String objectName) {
        return getInputStream(ossProperties.getBucketName(), objectName);
    }

    /**
     * 获取指定 Bucket 中对象的完整输入流。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象 key
     * @return 对象输入流
     */
    public InputStream getInputStream(String bucketName, String objectName) {
        return handleRequest(() ->
                client.getObject(buildGetRequest(bucketName, objectName),
                                AsyncResponseTransformer.toBytes())
                        .thenApply(rb -> toInputStream(rb.asByteBuffer())));
    }

    /**
     * 获取指定 Bucket 中对象某个字节区间的输入流。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象 key
     * @param range      字节区间，例如 `bytes=0-1023`
     * @return 对应区间的输入流
     */
    public InputStream getInputStream(String bucketName, String objectName, String range) {
        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(bucketName).key(Util.formatPath(objectName)).range(range).build();
        return handleRequest(() ->
                client.getObject(req, AsyncResponseTransformer.toBytes())
                        .thenApply(rb -> toInputStream(rb.asByteBuffer())));
    }

    // ----------------------------------------------------------------
    // 文件下载到本地
    // ----------------------------------------------------------------

    /**
     * 下载默认 Bucket 中的对象到本地。
     *
     * @param objectName    对象 key
     * @param localFilePath 本地文件路径或目录路径
     * @return 下载后的本地文件
     */
    public File getFile(String objectName, String localFilePath) {
        return getFile(ossProperties.getBucketName(), objectName, localFilePath);
    }

    /**
     * 下载指定 Bucket 中的对象到本地。
     *
     * @param bucketName    Bucket 名称
     * @param objectName    对象 key
     * @param localFilePath 本地文件路径或目录路径
     * @return 下载后的本地文件
     */
    public File getFile(String bucketName, String objectName, String localFilePath) {
        File outputFile = new File(localFilePath);
        outputFile.getParentFile().mkdirs();
        if (!Util.checkIsFile(localFilePath)) {
            outputFile.mkdirs();
            outputFile = new File(Util.formatPath(localFilePath) + Util.getFilename(objectName));
        }
        File finalFile = outputFile;
        handleRequest(() -> client.getObject(buildGetRequest(bucketName, objectName),
                AsyncResponseTransformer.toFile(finalFile)));
        return outputFile;
    }

    /**
     * 下载默认 Bucket 下整个目录前缀到本地。
     *
     * @param objectName    目录前缀
     * @param localFilePath 本地目录
     */
    public void getFolder(String objectName, String localFilePath) {
        getFolder(ossProperties.getBucketName(), objectName, localFilePath);
    }

    /**
     * 下载指定 Bucket 下整个目录前缀到本地。
     *
     * @param bucketName    Bucket 名称
     * @param objectName    目录前缀
     * @param localFilePath 本地目录
     */
    public void getFolder(String bucketName, String objectName, String localFilePath) {
        List<S3Object> objects = listObject(bucketName, objectName, null);
        if (!localFilePath.endsWith(File.separator)) {
            localFilePath += File.separator;
        }
        for (S3Object s3Object : objects) {
            String relativePath = s3Object.key()
                    .replace(objectName + "/", "")
                    .replace("/", File.separator);
            getFile(bucketName, s3Object.key(), localFilePath + relativePath);
        }
    }

    // ----------------------------------------------------------------
    // 预览/下载核心逻辑（不依赖 Servlet API，由 context 抽象适配）
    // ----------------------------------------------------------------

    /**
     * 预览或下载文件，支持 Range 分段请求。
     * <p>
     * HTTP 响应写入通过 {@link OssPreviewContext} 抽象，由各 starter 实现
     * javax.servlet / jakarta.servlet 适配，core 模块不引入任何 Servlet 依赖。
     *
     * @param context    HTTP 请求/响应抽象
     * @param objectName 对象 key
     * @param isDownload true=attachment 下载, false=inline 预览
     */
    public void previewObject(OssPreviewContext context, String objectName,
                              boolean isDownload) throws IOException {
        if (Util.isBlank(objectName)) {
            return;
        }

        if (objectName.contains("%")) {
            objectName = URLDecoder.decode(objectName, StandardCharsets.UTF_8.name());
        }

        try {
            String fileName = Util.getFilename(objectName);
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name())
                    .replace("+", "%20");

            ObjectInfo objectInfo = getObjectInfo(objectName);
            if (objectInfo == null) {
                context.sendNotFound();
                return;
            }

            long fileSize = objectInfo.getSize();
            context.setContentType(Util.getContentType(objectName));
            String disposition = isDownload ? "attachment" : "inline";
            context.setHeader("Content-Disposition",
                    disposition + "; filename=\"" + encodedFileName
                            + "\"; filename*=UTF-8''" + encodedFileName);
            context.setHeader("Accept-Ranges", "bytes");

            if ("HEAD".equals(context.getMethod())) {
                context.setContentLengthLong(fileSize);
                return;
            }

            String rangeHeader = context.getRangeHeader();
            if (rangeHeader == null) {
                serveFullContent(context, objectName, fileSize);
            } else {
                serveRangeContent(context, objectName, fileSize, rangeHeader);
            }
        } catch (NoSuchKeyException e) {
            context.sendNotFound();
        } catch (IOException e) {
            if (!"Broken pipe".equals(e.getMessage())) {
                throw e;
            }
        }
    }

    // ----------------------------------------------------------------
    // 树形结构构建（私有）
    // ----------------------------------------------------------------

    /**
     * 获取默认 Bucket 下的完整目录树。
     *
     * @param path 根目录前缀
     * @return 根节点；无命中时返回 {@code null}
     */
    public ObjectTreeNode getTreeList(String path) {
        return getTreeList(ossProperties.getBucketName(), path);
    }

    /**
     * 获取指定 Bucket 下的完整目录树。
     *
     * @param bucketName Bucket 名称
     * @param path       根目录前缀
     * @return 根节点；无命中时返回 {@code null}
     */
    public ObjectTreeNode getTreeList(String bucketName, String path) {
        return buildTree(listObject(bucketName, path), path);
    }

    /**
     * 按关键字搜索默认 Bucket 下的目录树。
     *
     * @param path    根目录前缀
     * @param keyword 关键字
     * @return 根节点；无命中时返回 {@code null}
     */
    public ObjectTreeNode getTreeListByName(String path, String keyword) {
        return getTreeListByName(ossProperties.getBucketName(), path, keyword);
    }

    /**
     * 按关键字搜索指定 Bucket 下的目录树。
     *
     * @param bucketName Bucket 名称
     * @param path       根目录前缀
     * @param keyword    关键字
     * @return 根节点；无命中时返回 {@code null}
     */
    public ObjectTreeNode getTreeListByName(String bucketName, String path, String keyword) {
        return buildTree(listObject(bucketName, path, keyword), path);
    }

    private ObjectTreeNode buildTree(List<S3Object> objects, String objectName) {
        if (objects == null || objects.isEmpty()) {
            // 无命中时返回空，避免把查询路径误表达成真实存在的目录节点。
            return null;
        }
        String rootName = extractRootName(objectName);
        ObjectTreeNode root = new ObjectTreeNode(rootName, objectName,
                getDomain() + objectName, null, "folder", 0, null);
        for (S3Object obj : objects) {
            String remaining = obj.key().startsWith(objectName + "/")
                    ? obj.key().substring(objectName.length() + 1) : obj.key();
            addNode(root, remaining, obj);
        }
        return root;
    }

    private ObjectTreeNode buildFolderTree(List<S3Object> objects, String objectName) {
        String rootName = extractRootName(objectName);
        ObjectTreeNode root = new ObjectTreeNode(rootName, objectName,
                getDomain() + objectName, null, "folder", 0, null);
        for (S3Object obj : objects) {
            String remaining = obj.key().startsWith(objectName + "/")
                    ? obj.key().substring(objectName.length() + 1) : obj.key();
            addFolderNode(root, remaining);
        }
        return root;
    }

    private static String extractRootName(String objectName) {
        if (Util.isBlank(objectName)) {
            return "";
        }
        int i = objectName.lastIndexOf('/');
        return i > 0 ? objectName.substring(i + 1) : objectName;
    }

    private void addNode(ObjectTreeNode parent, String remaining, S3Object object) {
        if (Util.isBlank(remaining)) {
            return;
        }
        int slashIdx = remaining.indexOf('/');
        if (slashIdx == -1) {
            parent.addChild(new ObjectTreeNode(remaining, object.key(),
                    getDomain() + object.key(), Date.from(object.lastModified()),
                    "file", object.size(), Util.getExtension(object.key())));
        } else {
            String folderName = remaining.substring(0, slashIdx);
            String newRemaining = remaining.substring(slashIdx + 1);
            ObjectTreeNode folder = findOrCreateFolder(parent, folderName);
            addNode(folder, newRemaining, object);
        }
    }

    private void addFolderNode(ObjectTreeNode parent, String remaining) {
        int slashIdx = remaining.indexOf('/');
        if (slashIdx == -1) {
            return;
        }
        String folderName = remaining.substring(0, slashIdx);
        String newRemaining = remaining.substring(slashIdx + 1);
        ObjectTreeNode folder = findOrCreateFolder(parent, folderName);
        addFolderNode(folder, newRemaining);
    }

    private ObjectTreeNode findOrCreateFolder(ObjectTreeNode parent, String folderName) {
        if (parent.getChildren() != null) {
            for (ObjectTreeNode child : parent.getChildren()) {
                if ("folder".equals(child.getType()) && folderName.equals(child.getName())) {
                    return child;
                }
            }
        }
        String uri = Util.isBlank(parent.getUri())
                ? folderName : parent.getUri() + "/" + folderName;
        ObjectTreeNode folder = new ObjectTreeNode(folderName, uri,
                getDomain() + uri, null, "folder", 0, null);
        parent.addChild(folder);
        return folder;
    }

    // ----------------------------------------------------------------
    // 私有工具方法
    // ----------------------------------------------------------------

    private GetObjectRequest buildGetRequest(String bucketName, String objectName) {
        return GetObjectRequest.builder()
                .bucket(bucketName).key(Util.formatPath(objectName)).build();
    }

    private static InputStream toInputStream(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return new ByteArrayInputStream(bytes);
    }

    private void serveFullContent(OssPreviewContext context, String objectName,
                                  long fileSize) throws IOException {
        context.setContentLengthLong(fileSize);
        try (InputStream in = getInputStream(objectName);
             OutputStream out = context.getOutputStream()) {
            pipe(in, out, fileSize);
        }
    }

    private void serveRangeContent(OssPreviewContext context, String objectName,
                                   long fileSize, String rangeHeader) throws IOException {
        long[] range = parseRange(rangeHeader, fileSize);
        long start = range[0], end = range[1];
        long contentLength = end - start + 1;

        context.setStatus(206);
        context.setHeader("Content-Length", String.valueOf(contentLength));
        context.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);

        try (InputStream in = getInputStream(ossProperties.getBucketName(), objectName, rangeHeader);
             OutputStream out = context.getOutputStream()) {
            pipe(in, out, contentLength);
        }
    }

    private static long[] parseRange(String rangeHeader, long fileSize) {
        String rangeValue = rangeHeader.split("=")[1];
        String[] parts = rangeValue.split("-");
        long start = Long.parseLong(parts[0]);
        long end = parts.length > 1 && !parts[1].isEmpty()
                ? Long.parseLong(parts[1]) : fileSize - 1;
        return new long[]{start, end};
    }

    private static void pipe(InputStream in, OutputStream out, long maxBytes) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long remaining = maxBytes;
        int read;
        while (remaining > 0
                && (read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
            out.write(buffer, 0, read);
            remaining -= read;
        }
    }
}
