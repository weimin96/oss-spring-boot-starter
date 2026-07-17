package com.wiblog.oss.service;

import com.wiblog.oss.bean.BucketInfo;
import com.wiblog.oss.bean.LazyDataList;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;
import com.wiblog.oss.bean.ReadObjectRangeCommand;
import com.wiblog.oss.bean.StoredObject;
import com.wiblog.oss.config.OssClientOptions;
import com.wiblog.oss.exception.OssException;
import com.wiblog.oss.util.Util;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseInputStream;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 查询操作类（核心逻辑，不依赖任何 Servlet API）
 * <p>
 * previewObject 相关的 HTTP 响应写入逻辑抽象为 {@link OssPreviewContext}，
 * 由各 Spring Boot 版本 starter 实现具体的 Servlet 适配。
 *
 * @author panwm
 */
@Slf4j
public class QueryOperations extends Operations implements OssQueryService {

    /**
     * 预览/下载时的 IO 缓冲区大小 4KB
     */
    private static final int BUFFER_SIZE = 4 * 1024;

    /**
     * 列举对象时每页最大数量
     */
    private static final int LIST_MAX_KEYS = 1000;

    public QueryOperations(OssClientOptions ossProperties, S3AsyncClient client,
                           S3TransferManager transferManager) {
        super(ossProperties, client, transferManager);
    }

    /**
     * 返回当前查询操作绑定的内部选项。
     *
     * @return 内部选项
     */
    public OssClientOptions getOssProperties() {
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
    @Override
    public boolean testConnect() {
        return testConnectForBucket();
    }

    /**
     * 测试指定 Bucket 是否可访问。
     *
     * @param bucketName Bucket 名称
     * @return 可访问返回 {@code true}
     */
    @Override
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
    @Override
    public boolean testConnectForBucket() {
        return testConnectForBucket(ossProperties.getBucketName());
    }

    /**
     * 列举当前凭证可见的全部 Bucket。
     *
     * @return Bucket 列表
     */
    @Override
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
    @Override
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
    @Override
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
        String prefix = Util.normalizeObjectPrefix(path);

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
    @Override
    public LazyDataList<ObjectInfo> lazyList(String path, int maxKeys, String continuationToken) {
        return lazyList(ossProperties.getBucketName(), path, maxKeys, continuationToken);
    }

    /**
     * 查询指定 Bucket 的懒加载分页列表。
     *
     * <p>第一页会额外补充下一层级目录节点，
     * 目的是让调用方在”分页文件 + 目录结构”并存的界面里一次拿到可展示数据。</p>
     *
     * @param bucketName        Bucket 名称
     * @param path              查询前缀
     * @param maxKeys           单次返回上限
     * @param continuationToken 分页游标
     * @return 懒加载分页结果
     */
    @Override
    public LazyDataList<ObjectInfo> lazyList(String bucketName, String path,
                                             int maxKeys, String continuationToken) {
        if (maxKeys <= 0) {
            maxKeys = LIST_MAX_KEYS;
        } else if (maxKeys > LIST_MAX_KEYS) {
            maxKeys = LIST_MAX_KEYS;
        }
        LazyDataList<ObjectInfo> resultList = new LazyDataList<>();

        ListObjectsV2Request.Builder builder = ListObjectsV2Request.builder()
                .bucket(bucketName).prefix(Util.normalizeObjectPrefix(path))
                .maxKeys(maxKeys).delimiter("/");

        if (continuationToken != null && !continuationToken.trim().isEmpty()) {
            builder.continuationToken(continuationToken);
        } else {
            resultList.addAll(listNextLevelFolder(bucketName, path));
        }

        ListObjectsV2Response response = client.listObjectsV2(builder.build()).join();
        response.contents().stream()
                .filter(e -> e.size() > 0)
                .map(e -> buildObjectInfo(e.key(), Date.from(e.lastModified()), e.size()).setType("file"))
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
    @Override
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
    @Override
    public List<ObjectTreeNode> listNextLevel(String bucketName, String path) {
        List<ObjectTreeNode> resultList = new ArrayList<>();
        String prefix = Util.normalizeObjectPrefix(path);

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
    @Override
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
    @Override
    public List<ObjectTreeNode> getFolderTreeList(String bucketName, String path) {
        String prefix = Util.normalizeObjectPrefix(path);
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
    @Override
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
    @Override
    public List<ObjectInfo> listNextLevelFolder(String bucketName, String path) {
        List<ObjectInfo> resultList = new ArrayList<>();
        String prefix = Util.normalizeObjectPrefix(path);
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName).prefix(prefix).delimiter("/").build();
        Set<String> seen = new HashSet<>(64);
        client.listObjectsV2Paginator(request).subscribe(response ->
                response.commonPrefixes().stream()
                        .map(CommonPrefix::prefix)
                        .filter(seen::add)
                        .map(this::buildTreeNode)
                        .map(node -> ObjectInfo.builder()
                                .uri(node.getUri())
                                .url(node.getUrl())
                                .name(node.getName())
                                .type(node.getType())
                                .build())
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
    @Override
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
    @Override
    public boolean checkExist(String bucketName, String objectName) {
        if (Util.isBlank(bucketName)) {
            throw new IllegalArgumentException("Bucket 名称不能为空");
        }
        if (Util.isBlank(objectName)) {
            throw new IllegalArgumentException("对象 key 不能为空");
        }
        String objectKey = normalizeObjectKey(objectName);
        try {
            executeRequestStrict(() -> client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName).key(objectKey).build()));
            return true;
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (NoSuchBucketException exception) {
            throw OssException.bucketNotFound(bucketName);
        } catch (S3Exception exception) {
            String errorCode = exception.awsErrorDetails() == null
                    ? null : exception.awsErrorDetails().errorCode();
            if ("NoSuchBucket".equals(errorCode)) {
                throw OssException.bucketNotFound(bucketName);
            }
            if (exception.statusCode() == 404 || "NoSuchKey".equals(errorCode)) {
                return false;
            }
            if (exception.statusCode() == 403 || "AccessDenied".equals(errorCode)) {
                throw new OssException("OBJECT_READ_FORBIDDEN",
                        "没有对象读取权限：" + objectKey, exception);
            }
            throw new OssException("OBJECT_HEAD_FAILED",
                    "检查对象是否存在失败：" + objectKey, exception);
        } catch (OssException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OssException("OBJECT_HEAD_FAILED",
                    "检查对象是否存在失败：" + objectKey, exception);
        }
    }

    /**
     * 查询默认 Bucket 中对象的元数据。
     *
     * @param objectName 对象 key
     * @return 对象信息
     */
    @Override
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
    @Override
    public ObjectInfo getObjectInfo(String bucketName, String objectName) {
        String objectKey = normalizeObjectKey(objectName);
        HeadObjectRequest req = HeadObjectRequest.builder()
                .bucket(bucketName).key(objectKey).build();
        HeadObjectResponse response = handleRequest(() -> client.headObject(req));
        return buildObjectInfo(objectKey, response);
    }

    @Override
    public StoredObject headObject(String bucket, String key) {
        if (Util.isBlank(bucket)) {
            throw new IllegalArgumentException("Bucket 名称不能为空");
        }
        if (Util.isBlank(key)) {
            throw new IllegalArgumentException("对象 key 不能为空");
        }
        String objectKey = normalizeObjectKey(key);
        HeadObjectResponse response = requireSuccessfulRequest(() -> client.headObject(HeadObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .checksumMode(ChecksumMode.ENABLED)
                        .build()),
                "OBJECT_HEAD_FAILED", "读取对象元数据失败：" + objectKey);
        return buildStoredObject(bucket, objectKey, response);
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
    @Override
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
    @Override
    public String getContent(String bucketName, String objectName) {
        return handleRequest(() -> client.getObject(buildGetRequest(bucketName, objectName),
                        AsyncResponseTransformer.toBytes())
                .thenApply(rb -> StandardCharsets.UTF_8.decode(rb.asByteBuffer()).toString()));
    }

    /**
     * 返回当前查询实现使用的默认 Bucket。
     *
     * @return 默认 Bucket 名称
     */
    @Override
    public String getDefaultBucketName() {
        return ossProperties.getBucketName();
    }

    /**
     * 获取默认 Bucket 中对象的输入流。
     *
     * @param objectName 对象 key
     * @return 对象输入流
     */
    @Override
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
    @Override
    public InputStream getInputStream(String bucketName, String objectName) {
        return openObjectStream(buildGetRequest(bucketName, objectName));
    }

    /**
     * 获取对象指定字节区间的输入流。
     *
     * @param command 字节区间读取命令
     * @return 对应区间的输入流
     */
    @Override
    public InputStream getInputStream(ReadObjectRangeCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("读取命令不能为空");
        }
        if (Util.isBlank(command.key())) {
            throw new IllegalArgumentException("对象 key 不能为空");
        }
        if (command.offset() < 0) {
            throw new IllegalArgumentException("读取偏移量不能小于 0");
        }
        if (command.length() <= 0) {
            throw new IllegalArgumentException("读取长度必须大于 0");
        }
        if (command.offset() > Long.MAX_VALUE - (command.length() - 1)) {
            throw new IllegalArgumentException("读取区间超出 long 范围");
        }

        String bucketName = Util.isBlank(command.bucket())
                ? getDefaultBucketName() : command.bucket();
        if (Util.isBlank(bucketName)) {
            throw new IllegalArgumentException("Bucket 名称不能为空，且当前实现未配置默认 Bucket");
        }
        long end = command.offset() + command.length() - 1;
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(normalizeObjectKey(command.key()))
                .range("bytes=" + command.offset() + "-" + end)
                .build();
        return openObjectStream(request);
    }

    /**
     * 获取指定 Bucket 中对象某个字节区间的输入流。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象 key
     * @param range      字节区间，例如 `bytes=0-1023`
     * @return 对应区间的输入流
     */
    @Deprecated
    @Override
    public InputStream getInputStream(String bucketName, String objectName, String range) {
        if (Util.isBlank(range)) {
            throw new IllegalArgumentException("读取区间不能为空");
        }
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(normalizeObjectKey(objectName))
                .range(range)
                .build();
        return openObjectStream(request);
    }

    private InputStream openObjectStream(GetObjectRequest request) {
        try {
            return executeRequestStrict(() -> client.getObject(
                    request, AsyncResponseTransformer.toBlockingInputStream()));
        } catch (NoSuchBucketException e) {
            throw OssException.bucketNotFound(request.bucket());
        } catch (NoSuchKeyException e) {
            throw OssException.objectNotFound(request.key());
        } catch (S3Exception e) {
            String errorCode = e.awsErrorDetails() == null
                    ? null : e.awsErrorDetails().errorCode();
            if ("NoSuchBucket".equals(errorCode)) {
                throw OssException.bucketNotFound(request.bucket());
            }
            if (e.statusCode() == 404 || "NoSuchKey".equals(errorCode)) {
                throw OssException.objectNotFound(request.key());
            }
            if (e.statusCode() == 416 || "InvalidRange".equals(errorCode)) {
                throw new OssException("OBJECT_RANGE_NOT_SATISFIABLE",
                        "对象读取区间无效：" + request.key(), e);
            }
            if (e.statusCode() == 403 || "AccessDenied".equals(errorCode)) {
                throw new OssException("OBJECT_READ_FORBIDDEN",
                        "没有对象读取权限：" + request.key(), e);
            }
            throw new OssException("OBJECT_READ_FAILED",
                    "读取对象失败：" + request.key(), e);
        } catch (OssException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new OssException("OBJECT_READ_FAILED",
                    "读取对象失败：" + request.key(), e);
        }
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
    @Override
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
    @Override
    public File getFile(String bucketName, String objectName, String localFilePath) {
        File outputFile = new File(localFilePath);
        File parentFile = outputFile.getParentFile();
        if (parentFile != null) {
            parentFile.mkdirs();
        }
        if (!Util.checkIsFile(localFilePath)) {
            outputFile.mkdirs();
            outputFile = new File(Util.formatPath(localFilePath) + Util.getFilename(objectName));
        }
        File finalFile = outputFile;
        requireSuccessfulRequest(() -> client.getObject(buildGetRequest(bucketName, objectName),
                        AsyncResponseTransformer.toFile(finalFile)),
                "OBJECT_DOWNLOAD_FAILED",
                "下载对象失败：" + normalizeObjectKey(objectName));
        return outputFile;
    }

    /**
     * 下载默认 Bucket 下整个目录前缀到本地。
     *
     * @param objectName    目录前缀
     * @param localFilePath 本地目录
     */
    @Override
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
    @Override
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

    /**
     * 将默认 Bucket 下某个前缀对应的对象集合流式写成 ZIP。
     *
     * <p>S3 没有真实文件夹概念，因此这里把 path 视为 prefix，
     * 先列举真实对象，再按相对路径写入 ZIP，避免返回一个空归档伪装成成功结果。</p>
     *
     * @param path         对象前缀
     * @param outputStream ZIP 输出目标
     */
    @Override
    public void writeFolderAsZip(String path, OutputStream outputStream) throws IOException {
        writeFolderAsZip(ossProperties.getBucketName(), path, outputStream);
    }

    /**
     * 将指定 Bucket 下某个前缀对应的对象集合流式写成 ZIP。
     *
     * <p>该能力只负责对象枚举和 ZIP 写出，不关心 HTTP 响应头；
     * Web 层可以复用这条读链路，把 ZIP 写到 Servlet 输出流，也可以在自定义业务中写到任意输出目标。</p>
     *
     * @param bucketName   Bucket 名称
     * @param path         对象前缀
     * @param outputStream ZIP 输出目标
     */
    @Override
    public void writeFolderAsZip(String bucketName, String path, OutputStream outputStream) throws IOException {
        FolderZipExportPlan exportPlan = prepareFolderZipExportPlan(bucketName, path);
        ZipOutputStream zipOutputStream = null;
        try {
            for (S3Object object : exportPlan.getObjects()) {
                try (InputStream inputStream = openObjectStreamForZip(exportPlan.getBucketName(), object.key())) {
                    if (zipOutputStream == null) {
                        zipOutputStream = new ZipOutputStream(
                                new NonClosingOutputStream(outputStream), StandardCharsets.UTF_8);
                    }
                    writeObjectAsZipEntry(exportPlan.getPrefix(), object, inputStream, zipOutputStream);
                } catch (IOException e) {
                    throw new OssException("OBJECT_READ_FAILED", "读取对象失败：" + object.key(), e);
                }
            }
            if (zipOutputStream != null) {
                zipOutputStream.finish();
            }
        } finally {
            if (zipOutputStream != null) {
                zipOutputStream.close();
            }
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
    @Override
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
    @Override
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
    @Override
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
    @Override
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
    @Override
    public ObjectTreeNode getTreeListByName(String bucketName, String path, String keyword) {
        return buildTree(listObject(bucketName, path, keyword), path);
    }

    private ObjectTreeNode buildTree(List<S3Object> objects, String objectName) {
        if (objects == null || objects.isEmpty()) {
            // 无命中时返回空，避免把查询路径误表达成真实存在的目录节点。
            return null;
        }
        String normalizedRootUri = trimTrailingSlash(objectName);
        String rootName = extractRootName(normalizedRootUri);
        ObjectTreeNode root = new ObjectTreeNode(rootName, normalizedRootUri,
                getDomain() + normalizedRootUri, null, "folder", 0, null);
        for (S3Object obj : objects) {
            String remaining = subtractRootPrefix(obj.key(), objectName);
            addNode(root, remaining, obj);
        }
        return root;
    }

    private ObjectTreeNode buildFolderTree(List<S3Object> objects, String objectName) {
        String normalizedRootUri = trimTrailingSlash(objectName);
        String rootName = extractRootName(normalizedRootUri);
        ObjectTreeNode root = new ObjectTreeNode(rootName, normalizedRootUri,
                getDomain() + normalizedRootUri, null, "folder", 0, null);
        for (S3Object obj : objects) {
            String remaining = subtractRootPrefix(obj.key(), objectName);
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
        String uri = appendFolderUri(parent.getUri(), folderName);
        ObjectTreeNode folder = new ObjectTreeNode(folderName, uri,
                getDomain() + uri, null, "folder", 0, null);
        parent.addChild(folder);
        return folder;
    }

    private static String subtractRootPrefix(String key, String rootPath) {
        String normalizedRoot = trimTrailingSlash(rootPath);
        if (Util.isBlank(normalizedRoot)) {
            return key;
        }
        String prefix = normalizedRoot + "/";
        return key.startsWith(prefix) ? key.substring(prefix.length()) : key;
    }

    private static String appendFolderUri(String parentUri, String folderName) {
        String normalizedParentUri = trimTrailingSlash(parentUri);
        return Util.isBlank(normalizedParentUri)
                ? folderName
                : normalizedParentUri + "/" + folderName;
    }

    /**
     * 文件夹树中的目录节点对外暴露为语义路径，不应保留仅用于 S3 前缀查询的尾部 `/`。
     */
    private static String trimTrailingSlash(String path) {
        return path != null && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    // ----------------------------------------------------------------
    // 私有工具方法
    // ----------------------------------------------------------------

    private GetObjectRequest buildGetRequest(String bucketName, String objectName) {
        return GetObjectRequest.builder()
                .bucket(bucketName).key(normalizeObjectKey(objectName)).build();
    }

    private static String normalizeObjectKey(String objectName) {
        if (objectName == null) {
            return null;
        }
        String objectKey = objectName.replace('\\', '/');
        return objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
    }

    /**
     * ZIP 导出在开始写出之前先做对象枚举和目录占位过滤，
     * 这样能把“前缀为空”或“没有真实对象”收敛成明确异常，而不是生成一个空 ZIP 误导调用方。
     */
    private FolderZipExportPlan prepareFolderZipExportPlan(String bucketName, String path) {
        String prefix = formatZipPrefix(path);
        List<S3Object> objects = listObject(bucketName, prefix, null).stream()
                .filter(object -> isRealFolderObject(object, prefix))
                .collect(Collectors.toList());
        if (objects.isEmpty()) {
            throw new OssException("OBJECT_NOT_FOUND", "未找到前缀下的对象：" + prefix);
        }
        return new FolderZipExportPlan(bucketName, prefix, objects);
    }

    private String formatZipPrefix(String path) {
        if (Util.isBlank(path) || "/".equals(path)) {
            throw new OssException("INVALID_PATH", "文件夹前缀不能为空");
        }
        String prefix = Util.normalizeObjectPrefix(path);
        if (Util.isBlank(prefix)) {
            throw new OssException("INVALID_PATH", "文件夹前缀不能为空");
        }
        return prefix;
    }

    private boolean isRealFolderObject(S3Object object, String prefix) {
        if (object == null || Util.isBlank(object.key())) {
            return false;
        }
        String key = object.key();
        if (!key.startsWith(prefix)) {
            return false;
        }
        return !key.endsWith("/");
    }

    /**
     * 先成功打开对象流，再创建 ZIP 条目，
     * 是为了把“对象不存在 / 无法读取”尽量暴露在写出第一批 ZIP 字节之前，便于上层返回明确错误响应。
     */
    private void writeObjectAsZipEntry(String prefix, S3Object object,
                                       InputStream inputStream, ZipOutputStream zipOutputStream) throws IOException {
        String entryName = buildZipEntryName(object.key(), prefix);
        boolean entryOpened = false;
        try {
            ZipEntry entry = new ZipEntry(entryName);
            if (object.lastModified() != null) {
                entry.setTime(object.lastModified().toEpochMilli());
            }
            zipOutputStream.putNextEntry(entry);
            entryOpened = true;
            pipe(inputStream, zipOutputStream);
        } finally {
            if (entryOpened) {
                zipOutputStream.closeEntry();
            }
        }
    }

    private String buildZipEntryName(String objectKey, String prefix) {
        String entryName = objectKey.startsWith(prefix)
                ? objectKey.substring(prefix.length())
                : objectKey;
        if (Util.isBlank(entryName) || entryName.endsWith("/")) {
            throw new OssException("INVALID_ZIP_ENTRY", "对象无法转换为 ZIP 条目：" + objectKey);
        }
        return entryName;
    }

    private InputStream openObjectStreamForZip(String bucketName, String objectKey) {
        try {
            ResponseInputStream<GetObjectResponse> responseInputStream = executeRequestStrict(() ->
                    client.getObject(buildGetRequest(bucketName, objectKey), AsyncResponseTransformer.toBlockingInputStream()));
            if (responseInputStream == null) {
                throw OssException.objectNotFound(objectKey);
            }
            return responseInputStream;
        } catch (NoSuchKeyException e) {
            throw OssException.objectNotFound(objectKey);
        } catch (S3Exception e) {
            throw new OssException("OBJECT_READ_FAILED", "读取对象失败：" + objectKey, e);
        }
    }

    private void serveFullContent(OssPreviewContext context, String objectName,
                                  long fileSize) throws IOException {
        InputStream inputStream = getInputStream(objectName);
        if (inputStream == null) {
            context.sendNotFound();
            return;
        }
        context.setContentLengthLong(fileSize);
        try (InputStream in = inputStream;
             OutputStream out = context.getOutputStream()) {
            pipe(in, out, fileSize);
        }
    }

    private void serveRangeContent(OssPreviewContext context, String objectName,
                                   long fileSize, String rangeHeader) throws IOException {
        long[] range = parseRange(rangeHeader, fileSize);
        if (range == null) {
            context.setStatus(416);
            context.setHeader("Content-Range", "bytes */" + fileSize);
            context.setContentLengthLong(0);
            return;
        }
        long start = range[0], end = range[1];
        long contentLength = end - start + 1;

        InputStream inputStream = getInputStream(new ReadObjectRangeCommand(
                ossProperties.getBucketName(), objectName, start, contentLength));
        if (inputStream == null) {
            context.sendNotFound();
            return;
        }
        context.setStatus(206);
        context.setHeader("Content-Length", String.valueOf(contentLength));
        context.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);

        try (InputStream in = inputStream;
             OutputStream out = context.getOutputStream()) {
            pipe(in, out, contentLength);
        }
    }

    private static long[] parseRange(String rangeHeader, long fileSize) {
        if (fileSize <= 0 || rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            return null;
        }
        String rangeValue = rangeHeader.substring("bytes=".length()).trim();
        int dashIndex = rangeValue.indexOf('-');
        if (dashIndex < 0 || rangeValue.indexOf(',', dashIndex) >= 0) {
            return null;
        }
        String startPart = rangeValue.substring(0, dashIndex).trim();
        String endPart = rangeValue.substring(dashIndex + 1).trim();
        if (startPart.isEmpty() && endPart.isEmpty()) {
            return null;
        }

        long start;
        long end;
        try {
            if (startPart.isEmpty()) {
                long suffixLength = Long.parseLong(endPart);
                if (suffixLength <= 0) {
                    return null;
                }
                start = Math.max(0L, fileSize - suffixLength);
                end = fileSize - 1;
            } else {
                start = Long.parseLong(startPart);
                end = endPart.isEmpty() ? fileSize - 1 : Long.parseLong(endPart);
            }
        } catch (NumberFormatException exception) {
            return null;
        }
        if (start < 0 || start >= fileSize || end < start) {
            return null;
        }
        if (end >= fileSize) {
            end = fileSize - 1;
        }
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

    private static void pipe(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    /**
     * 关闭 ZIP 流时只允许结束归档，不应该把外部传入的真实输出流一并关闭；
     * 否则自定义调用方或 Servlet 容器无法继续控制响应生命周期。
     */
    private static final class NonClosingOutputStream extends FilterOutputStream {

        private NonClosingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }

    private static final class FolderZipExportPlan {

        private final String bucketName;
        private final String prefix;
        private final List<S3Object> objects;

        private FolderZipExportPlan(String bucketName, String prefix, List<S3Object> objects) {
            this.bucketName = bucketName;
            this.prefix = prefix;
            this.objects = objects;
        }

        private String getBucketName() {
            return bucketName;
        }

        private String getPrefix() {
            return prefix;
        }

        private List<S3Object> getObjects() {
            return objects;
        }
    }
}
