package com.wiblog.oss.service;

import com.wiblog.oss.bean.LazyDataList;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;
import com.wiblog.oss.bean.OssProperties;
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

    /** 预览/下载时的 IO 缓冲区大小 4KB */
    private static final int BUFFER_SIZE = 4 * 1024;

    /** 列举对象时每页最大数量 */
    private static final int LIST_MAX_KEYS = 1000;

    public QueryOperations(OssProperties ossProperties, S3AsyncClient client,
                           S3TransferManager transferManager) {
        super(ossProperties, client, transferManager);
    }

    public OssProperties getOssProperties() {
        return ossProperties;
    }

    // ----------------------------------------------------------------
    // 连接 / Bucket 检测
    // ----------------------------------------------------------------

    public boolean testConnect() {
        return testConnectForBucket();
    }

    public boolean testConnectForBucket(String bucketName) {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build()).join();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean testConnectForBucket() {
        return testConnectForBucket(ossProperties.getBucketName());
    }

    public List<Bucket> getAllBuckets() {
        return client.listBuckets().join().buckets();
    }

    // ----------------------------------------------------------------
    // 对象列表查询
    // ----------------------------------------------------------------

    public List<ObjectInfo> listObjects(String path) {
        return listObjects(ossProperties.getBucketName(), path);
    }

    public List<ObjectInfo> listObjects(String bucketName, String path) {
        return listObject(bucketName, path, null).stream()
                .map(e -> ObjectInfo.builder()
                        .uri(e.key())
                        .url(getDomain() + e.key())
                        .name(Util.getFilename(e.key()))
                        .uploadTime(Date.from(e.lastModified()))
                        .build())
                .collect(Collectors.toList());
    }

    public List<S3Object> listObject(String path) {
        return listObject(ossProperties.getBucketName(), path, null);
    }

    public List<S3Object> listObject(String bucketName, String path) {
        return listObject(bucketName, path, null);
    }

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

    public LazyDataList<ObjectInfo> lazyList(String path, int maxKeys, String continuationToken) {
        return lazyList(ossProperties.getBucketName(), path, maxKeys, continuationToken);
    }

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

    public List<ObjectTreeNode> listNextLevel(String path) {
        return listNextLevel(ossProperties.getBucketName(), path);
    }

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

    public List<ObjectTreeNode> getFolderTreeList(String path) {
        return getFolderTreeList(ossProperties.getBucketName(), path);
    }

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

    public List<ObjectInfo> listNextLevelFolder(String path) {
        return listNextLevelFolder(ossProperties.getBucketName(), path);
    }

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

    public boolean checkExist(String objectName) {
        return checkExist(ossProperties.getBucketName(), objectName);
    }

    public boolean checkExist(String bucketName, String objectName) {
        try {
            client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName).key(objectName).build()).join();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public ObjectInfo getObjectInfo(String objectName) {
        return getObjectInfo(ossProperties.getBucketName(), objectName);
    }

    public ObjectInfo getObjectInfo(String bucketName, String objectName) {
        HeadObjectRequest req = HeadObjectRequest.builder()
                .bucket(bucketName).key(objectName).build();
        HeadObjectResponse response = handleRequest(() -> client.headObject(req));
        return buildObjectInfo(objectName, response);
    }

    // ----------------------------------------------------------------
    // 内容读取
    // ----------------------------------------------------------------

    public String getContent(String objectName) {
        return getContent(ossProperties.getBucketName(), objectName);
    }

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

    public InputStream getInputStream(String objectName) {
        return getInputStream(ossProperties.getBucketName(), objectName);
    }

    public InputStream getInputStream(String bucketName, String objectName) {
        return handleRequest(() ->
                client.getObject(buildGetRequest(bucketName, objectName),
                                AsyncResponseTransformer.toBytes())
                        .thenApply(rb -> toInputStream(rb.asByteBuffer())));
    }

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

    public File getFile(String objectName, String localFilePath) {
        return getFile(ossProperties.getBucketName(), objectName, localFilePath);
    }

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

    public void getFolder(String objectName, String localFilePath) {
        getFolder(ossProperties.getBucketName(), objectName, localFilePath);
    }

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

    public ObjectTreeNode getTreeList(String path) {
        return getTreeList(ossProperties.getBucketName(), path);
    }

    public ObjectTreeNode getTreeList(String bucketName, String path) {
        return buildTree(listObject(bucketName, path), path);
    }

    public ObjectTreeNode getTreeListByName(String path, String keyword) {
        return getTreeListByName(ossProperties.getBucketName(), path, keyword);
    }

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
