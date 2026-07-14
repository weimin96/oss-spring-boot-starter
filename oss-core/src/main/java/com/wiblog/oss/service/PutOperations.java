package com.wiblog.oss.service;

import com.wiblog.oss.bean.CopyObjectCommand;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.PutObjectCommand;
import com.wiblog.oss.bean.StoredObject;
import com.wiblog.oss.bean.chunk.*;
import com.wiblog.oss.config.OssClientOptions;
import com.wiblog.oss.exception.OssException;
import com.wiblog.oss.util.Util;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.BlockingInputStreamAsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedUpload;
import software.amazon.awssdk.transfer.s3.model.Upload;
import software.amazon.awssdk.transfer.s3.model.UploadDirectoryRequest;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;
import software.amazon.awssdk.transfer.s3.model.UploadRequest;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 上传操作
 *
 * @author panwm
 */
@Slf4j
public class PutOperations extends Operations implements OssPutService {

    private static final int LIST_PARTS_MAX_PARTS = 1000;
    private static final String MOVE_STAGING_PREFIX = ".oss-staging/move/";

    /**
     * 创建上传操作门面。
     *
     * @param ossProperties   OSS 配置
     * @param client          S3 异步客户端
     * @param transferManager 传输管理器
     */
    public PutOperations(OssClientOptions ossProperties, S3AsyncClient client, S3TransferManager transferManager) {
        super(ossProperties, client, transferManager);
    }

    // ----------------------------------------------------------------
    // Bucket 操作
    // ----------------------------------------------------------------

    /**
     * 创建一个 Bucket；若已存在则跳过。
     *
     * <p>这里采用”存在即幂等成功”的语义，
     * 目的是让启动阶段的自动建桶和业务侧显式建桶都可以安全重复调用。</p>
     *
     * @param bucketName 待创建的 Bucket 名称
     */
    @Override
    public void createBucket(String bucketName) {
        if (!bucketExists(bucketName)) {
            CreateBucketRequest req = CreateBucketRequest.builder().bucket(bucketName).build();
            requireSuccessfulRequest(() -> client.createBucket(req),
                    "BUCKET_CREATE_FAILED",
                    "创建 Bucket 失败：" + bucketName);
            log.info("Bucket [{}] created", bucketName);
        }
    }

    private boolean bucketExists(String bucketName) {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build()).join();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ----------------------------------------------------------------
    // 文件上传 - InputStream
    // ----------------------------------------------------------------

    /**
     * 向默认 Bucket 上传一个输入流对象。
     *
     * @param path     目标目录
     * @param filename 文件名
     * @param in       文件输入流
     * @return 上传后的对象信息
     */
    @Override
    public ObjectInfo putObject(String path, String filename, InputStream in) {
        return putObject(ossProperties.getBucketName(), path, filename, in);
    }

    /**
     * 向指定 Bucket 上传一个输入流对象。
     *
     * @param bucketName Bucket 名称
     * @param path       目标目录
     * @param filename   文件名
     * @param in         文件输入流
     * @return 上传后的对象信息
     */
    @Override
    public ObjectInfo putObject(String bucketName, String path, String filename, InputStream in) {
        return putObjectForKey(bucketName, formatPath(path) + filename, in);
    }

    /**
     * 按完整对象 key 向默认 Bucket 上传输入流。
     *
     * @param objectName 完整对象 key
     * @param stream     文件输入流
     * @return 上传后的对象信息
     */
    @Override
    public ObjectInfo putObjectForKey(String objectName, InputStream stream) {
        return putObjectForKey(ossProperties.getBucketName(), objectName, stream);
    }

    /**
     * 上传 InputStream。
     *
     * @param bucketName Bucket 名称
     * @param objectName 完整对象 key
     * @param stream     文件输入流
     * @return 上传后的对象信息
     */
    @Override
    public ObjectInfo putObjectForKey(String bucketName, String objectName, InputStream stream) {
        String objectKey = Util.normalizeObjectKey(objectName);
        StoredObject storedObject = putObject(new PutObjectCommand(
                bucketName, objectKey, stream, null, Util.getContentType(objectKey),
                null, null, null, false));
        return buildObjectInfo(storedObject.key(), new Date(), storedObject.size());
    }

    @Override
    public StoredObject putObject(PutObjectCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("上传命令不能为空");
        }
        if (Util.isBlank(command.key())) {
            throw new IllegalArgumentException("对象 key 不能为空");
        }
        if (command.input() == null) {
            throw new IllegalArgumentException("对象输入流不能为空");
        }
        if (command.contentLength() != null && command.contentLength() < 0) {
            throw new IllegalArgumentException("对象内容长度不能为负数");
        }

        String bucket = Util.isBlank(command.bucket()) ? ossProperties.getBucketName() : command.bucket();
        String key = Util.normalizeObjectKey(command.key());
        BlockingInputStreamAsyncRequestBody body = AsyncRequestBody.forBlockingInputStream(command.contentLength());
        PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(Util.isBlank(command.contentType()) ? Util.getContentType(key) : command.contentType());
        if (command.contentLength() != null) {
            requestBuilder.contentLength(command.contentLength());
        }
        if (command.metadata() != null && !command.metadata().isEmpty()) {
            requestBuilder.metadata(command.metadata());
        }
        if (command.tags() != null && !command.tags().isEmpty()) {
            List<Tag> tags = command.tags().entrySet().stream()
                    .map(entry -> Tag.builder().key(entry.getKey()).value(entry.getValue()).build())
                    .collect(Collectors.toList());
            requestBuilder.tagging(Tagging.builder().tagSet(tags).build());
        }
        if (!Util.isBlank(command.checksumSha256())) {
            requestBuilder.checksumSHA256(command.checksumSha256());
        }
        if (command.createOnly()) {
            requestBuilder.ifNoneMatch("*");
        }
        UploadRequest uploadReq = UploadRequest.builder()
                .requestBody(body)
                .putObjectRequest(requestBuilder.build())
                .build();

        long fileSize;
        PutObjectResponse response;
        try {
            Upload upload = transferManager.upload(uploadReq);
            fileSize = body.writeInputStream(command.input());
            CompletedUpload completedUpload = upload.completionFuture().join();
            if (completedUpload == null || completedUpload.response() == null) {
                throw new OssException("OBJECT_UPLOAD_FAILED", "上传响应为空：" + key);
            }
            response = completedUpload.response();
        } catch (RuntimeException e) {
            body.cancel();
            if (e instanceof OssException) {
                throw e;
            }
            throw OssException.uploadFailed(key, e);
        }

        return new StoredObject(bucket, key, fileSize, response.eTag(),
                response.versionId(), response.checksumSHA256());
    }

    // ----------------------------------------------------------------
    // 文件上传 - File
    // ----------------------------------------------------------------

    /**
     * 向默认 Bucket 上传本地文件。
     *
     * @param path     目标目录
     * @param filename 目标文件名
     * @param file     本地文件
     * @return 上传后的对象信息
     */
    @Override
    public ObjectInfo putObject(String path, String filename, File file) {
        return putObject(ossProperties.getBucketName(), path, filename, file);
    }

    /**
     * 向指定 Bucket 上传本地文件。
     *
     * @param bucketName Bucket 名称
     * @param path       目标目录
     * @param filename   目标文件名
     * @param file       本地文件
     * @return 上传后的对象信息
     */
    @Override
    public ObjectInfo putObject(String bucketName, String path, String filename, File file) {
        return putObjectForKey(bucketName, formatPath(path) + filename, file);
    }

    /**
     * 按完整对象 key 向默认 Bucket 上传本地文件。
     *
     * @param objectName 完整对象 key
     * @param file       本地文件
     * @return 上传后的对象信息
     */
    @Override
    public ObjectInfo putObjectForKey(String objectName, File file) {
        return putObjectForKey(ossProperties.getBucketName(), objectName, file);
    }

    /**
     * 按完整对象 key 向指定 Bucket 上传本地文件。
     *
     * @param bucketName Bucket 名称
     * @param objectName 完整对象 key
     * @param file       本地文件
     * @return 上传后的对象信息
     */
    @Override
    public ObjectInfo putObjectForKey(String bucketName, String objectName, File file) {
        objectName = Util.normalizeObjectKey(objectName);
        PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(bucketName).key(objectName)
                .contentType(Util.getContentType(objectName))
                .build();
        UploadFileRequest uploadFileReq = UploadFileRequest.builder()
                .putObjectRequest(putReq).source(file).build();
        transferManager.uploadFile(uploadFileReq).completionFuture().join();
        return buildObjectInfo(objectName, new Date(), file.length());
    }

    // ----------------------------------------------------------------
    // 目录操作
    // ----------------------------------------------------------------

    /**
     * 在默认 Bucket 下创建一个目录占位对象。
     *
     * @param path 目录前缀
     * @return 创建后的目录对象信息
     */
    @Override
    public ObjectInfo mkdirs(String path) {
        return mkdirs(ossProperties.getBucketName(), path);
    }

    /**
     * 在指定 Bucket 下创建一个目录占位对象。
     *
     * <p>对象存储本身没有真实目录，这里通过写入一个以 `/` 结尾的空对象来稳定表达目录存在。</p>
     *
     * @param bucketName Bucket 名称
     * @param path       目录前缀
     * @return 创建后的目录对象信息
     */
    @Override
    public ObjectInfo mkdirs(String bucketName, String path) {
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucketName).key(formatPath(path)).build();
        requireSuccessfulRequest(() -> client.putObject(req, AsyncRequestBody.empty()),
                "DIRECTORY_CREATE_FAILED",
                "创建目录失败：" + formatPath(path));
        return buildObjectInfo(path, new Date(), 0);
    }

    /**
     * 上传整个目录到默认 Bucket。
     *
     * @param path   目标目录前缀
     * @param folder 本地目录
     */
    @Override
    public void putFolder(String path, File folder) {
        putFolder(path, folder, true);
    }

    /**
     * 上传整个目录到默认 Bucket，并控制是否保留目录名。
     *
     * @param path                目标目录前缀
     * @param folder              本地目录
     * @param isIncludeFolderName 是否把本地目录名拼入目标前缀
     */
    @Override
    public void putFolder(String path, File folder, boolean isIncludeFolderName) {
        putFolder(ossProperties.getBucketName(), path, folder, isIncludeFolderName);
    }

    /**
     * 上传整个目录到指定 Bucket。
     *
     * <p>目录上传复用了 TransferManager 的目录传输能力，
     * 是为了在大量文件场景下让 SDK 负责并发调度，而不是在业务层自己循环上传。</p>
     *
     * @param bucketName          Bucket 名称
     * @param path                目标目录前缀
     * @param folder              本地目录
     * @param isIncludeFolderName 是否把本地目录名拼入目标前缀
     */
    @Override
    public void putFolder(String bucketName, String path, File folder, boolean isIncludeFolderName) {
        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException("目录不存在: " + folder.getPath());
        }
        path = formatPath(path);
        if (isIncludeFolderName) {
            path += folder.getName() + "/";
        }
        UploadDirectoryRequest req = UploadDirectoryRequest.builder()
                .source(Paths.get(folder.getAbsolutePath()))
                .s3Prefix(path).bucket(bucketName).build();
        transferManager.uploadDirectory(req).completionFuture().join();
    }

    // ----------------------------------------------------------------
    // 拷贝 / 移动
    // ----------------------------------------------------------------

    /**
     * 在默认 Bucket 内复制对象。
     *
     * @param sourceKey 源对象 key
     * @param destKey   目标对象 key
     */
    @Override
    public void copyFile(String sourceKey, String destKey) {
        copyFile(ossProperties.getBucketName(), ossProperties.getBucketName(), sourceKey, destKey);
    }

    /**
     * 在指定源/目标 Bucket 之间复制对象。
     *
     * @param sourceBucket 源 Bucket
     * @param destBucket   目标 Bucket
     * @param sourceKey    源对象 key
     * @param destKey      目标对象 key
     */
    @Override
    public void copyFile(String sourceBucket, String destBucket, String sourceKey, String destKey) {
        CopyObjectRequest req = CopyObjectRequest.builder()
                .sourceBucket(sourceBucket).sourceKey(Util.normalizeObjectKey(sourceKey))
                .destinationBucket(destBucket).destinationKey(Util.normalizeObjectKey(destKey))
                .build();
        requireSuccessfulRequest(() -> client.copyObject(req),
                "OBJECT_COPY_FAILED",
                "复制对象失败：" + Util.normalizeObjectKey(sourceKey));
    }

    @Override
    public StoredObject copyObject(CopyObjectCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("复制命令不能为空");
        }
        if (Util.isBlank(command.sourceKey()) || Util.isBlank(command.destinationKey())) {
            throw new IllegalArgumentException("源对象 key 和目标对象 key 不能为空");
        }
        String sourceBucket = Util.isBlank(command.sourceBucket())
                ? ossProperties.getBucketName() : command.sourceBucket();
        String destinationBucket = Util.isBlank(command.destinationBucket())
                ? ossProperties.getBucketName() : command.destinationBucket();
        String sourceKey = Util.normalizeObjectKey(command.sourceKey());
        String destinationKey = Util.normalizeObjectKey(command.destinationKey());
        CopyObjectRequest request = CopyObjectRequest.builder()
                .sourceBucket(sourceBucket)
                .sourceKey(sourceKey)
                .destinationBucket(destinationBucket)
                .destinationKey(destinationKey)
                .build();
        requireSuccessfulRequest(() -> client.copyObject(request),
                "OBJECT_COPY_FAILED", "复制对象失败：" + sourceKey);
        HeadObjectResponse response = requireSuccessfulRequest(() -> client.headObject(HeadObjectRequest.builder()
                        .bucket(destinationBucket)
                        .key(destinationKey)
                        .checksumMode(ChecksumMode.ENABLED)
                        .build()),
                "OBJECT_HEAD_FAILED", "读取复制结果失败：" + destinationKey);
        return buildStoredObject(destinationBucket, destinationKey, response);
    }

    /**
     * 在默认 Bucket 内移动对象到目标目录。
     *
     * @param sourceObjectName     源对象 key
     * @param destinationDirectory 目标目录
     */
    @Override
    public void move(String sourceObjectName, String destinationDirectory) {
        move(ossProperties.getBucketName(), sourceObjectName, destinationDirectory);
    }

    /**
     * 在指定 Bucket 内移动对象到目标目录。
     *
     * <p>对象存储不支持真正的 rename。这里先复制到内部 staging key 并校验，
     * 再复制到最终位置；只有最终对象校验成功且 staging 已清理后才删除源对象。</p>
     *
     * @param bucketName           Bucket 名称
     * @param sourceObjectName     源对象 key
     * @param destinationDirectory 目标目录
     */
    @Override
    public void move(String bucketName, String sourceObjectName, String destinationDirectory) {
        String sourceKey = Util.normalizeObjectKey(sourceObjectName);
        String filename = Util.getFilename(sourceObjectName);
        String destKey = Util.formatPath(destinationDirectory) + filename;
        destKey = Util.normalizeObjectKey(destKey);
        StoredObject source = headMoveObject(bucketName, sourceKey, "MOVE_SOURCE_HEAD_FAILED", "读取源对象失败：");
        if (sourceKey.equals(destKey)) {
            return;
        }

        String stagingKey = MOVE_STAGING_PREFIX + UUID.randomUUID().toString();
        boolean stagingCleanupRequired = true;
        try {
            copyFile(bucketName, bucketName, sourceKey, stagingKey);
            StoredObject staging = headMoveObject(bucketName, stagingKey,
                    "MOVE_STAGING_HEAD_FAILED", "读取 staging 对象失败：");
            verifyMoveCopy(source, staging, "MOVE_STAGING_INVALID", "staging 对象校验失败：");

            copyFile(bucketName, bucketName, stagingKey, destKey);
            StoredObject destination = headMoveObject(bucketName, destKey,
                    "MOVE_DESTINATION_HEAD_FAILED", "读取最终对象失败：");
            verifyMoveCopy(source, destination, "MOVE_DESTINATION_INVALID", "最终对象校验失败：");

            deleteMoveObject(bucketName, stagingKey,
                    "MOVE_STAGING_DELETE_FAILED", "删除 staging 对象失败：");
            stagingCleanupRequired = false;
            deleteMoveObject(bucketName, sourceKey,
                    "MOVE_SOURCE_DELETE_FAILED", "删除源对象失败：");
        } catch (RuntimeException failure) {
            if (stagingCleanupRequired) {
                cleanupMoveStaging(bucketName, stagingKey, failure);
            }
            throw failure;
        }
    }

    private StoredObject headMoveObject(String bucketName, String key, String errorCode, String messagePrefix) {
        HeadObjectResponse response = requireSuccessfulRequest(() -> client.headObject(HeadObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .checksumMode(ChecksumMode.ENABLED)
                        .build()),
                errorCode, messagePrefix + key);
        return buildStoredObject(bucketName, key, response);
    }

    private void verifyMoveCopy(StoredObject source, StoredObject copied,
                                String errorCode, String messagePrefix) {
        boolean sizeMatches = source.size() == copied.size();
        boolean checksumMatches = Util.isBlank(source.checksumSha256())
                || source.checksumSha256().equals(copied.checksumSha256());
        if (!sizeMatches || !checksumMatches) {
            throw new OssException(errorCode, messagePrefix + copied.key());
        }
    }

    private void deleteMoveObject(String bucketName, String key, String errorCode, String messagePrefix) {
        requireSuccessfulRequest(() -> client.deleteObject(request -> request.bucket(bucketName).key(key).build()),
                errorCode, messagePrefix + key);
    }

    private void cleanupMoveStaging(String bucketName, String stagingKey, RuntimeException failure) {
        try {
            deleteMoveObject(bucketName, stagingKey,
                    "MOVE_STAGING_DELETE_FAILED", "删除 staging 对象失败：");
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    // ----------------------------------------------------------------
    // 分片上传
    // ----------------------------------------------------------------

    /**
     * 初始化一个分片上传任务。
     *
     * @param chunkTask 分片任务定义
     * @return S3 分片上传任务 ID
     */
    @Override
    public String initTask(ChunkTask chunkTask) {
        String objectName = formatPath(chunkTask.getPath()) + chunkTask.getFilename();
        CreateMultipartUploadResponse resp = client.createMultipartUpload(b -> b
                .bucket(ossProperties.getBucketName()).key(objectName)).join();
        return resp.uploadId();
    }

    /**
     * 上传单个分片。
     *
     * @param chunk 分片内容和分片元数据
     * @return 分片号与 ETag 信息
     */
    @Override
    public ChunkTarget chunk(ChunkUploadCommand chunk) {
        UploadPartRequest req = UploadPartRequest.builder()
                .bucket(ossProperties.getBucketName())
                .key(formatPath(chunk.getPath()) + chunk.getFilename())
                .uploadId(chunk.getUploadId())
                .partNumber(chunk.getChunkNumber())
                .contentLength(chunk.getContentLength())
                .build();
        try {
            ByteBuffer buf = ByteBuffer.wrap(chunk.getFileBytes());
            String etag = client.uploadPart(req, AsyncRequestBody.fromByteBuffer(buf)).join().eTag();
            ChunkTarget target = new ChunkTarget();
            target.setEtag(etag.replace("\"", ""));
            target.setPartNumber(chunk.getChunkNumber());
            return target;
        } catch (Exception e) {
            log.error("分片上传失败 file={} part={}", chunk.getFilename(), chunk.getChunkNumber(), e);
            throw OssException.uploadFailed(chunk.getFilename(), e);
        }
    }

    /**
     * 合并已上传的所有分片。
     *
     * <p>合并完成后会立即回查对象头信息，
     * 这样返回值能带上最终文件大小，而不是只返回名称和 URL。</p>
     *
     * @param chunkMerge 分片合并请求
     * @return 合并后的对象信息
     */
    @Override
    public ObjectInfo merge(ChunkMerge chunkMerge) {
        String objectName = formatPath(chunkMerge.getPath()) + chunkMerge.getFilename();
        // 处理 null 或空列表的情况
        List<ChunkTarget> chunkList = chunkMerge.getChunkTargetList();
        if (chunkList == null || chunkList.isEmpty()) {
            throw new IllegalArgumentException("分片列表不能为空，请确保所有分片已上传完成");
        }
        List<CompletedPart> parts = chunkMerge.getChunkTargetList().stream()
                .map(p -> CompletedPart.builder().partNumber(p.getPartNumber()).eTag(p.getEtag()).build())
                .sorted(Comparator.comparingInt(CompletedPart::partNumber))
                .collect(Collectors.toList());

        client.completeMultipartUpload(b -> b
                .bucket(ossProperties.getBucketName()).key(objectName)
                .uploadId(chunkMerge.getUploadId())
                .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())).join();

        // 合并成功后立即回查对象元数据，确保把最终文件大小返回给调用方，
        // 避免前端拿到一个只有名称和 URL 的不完整结果。
        HeadObjectResponse response = handleRequest(() -> client.headObject(HeadObjectRequest.builder()
                .bucket(ossProperties.getBucketName())
                .key(objectName)
                .build()));
        return buildObjectInfo(objectName, response);
    }

    /**
     * 查询某个分片上传任务已上传的分片列表。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象 key
     * @param uploadId   分片上传任务 ID
     * @return 已上传分片列表
     */
    @Override
    public List<ChunkPartInfo> listParts(String bucketName, String objectName, String uploadId) {
        List<ChunkPartInfo> partInfos = new ArrayList<>();
        Integer partNumberMarker = null;
        boolean hasNextPage;
        do {
            ListPartsRequest.Builder requestBuilder = ListPartsRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .uploadId(uploadId)
                    .maxParts(LIST_PARTS_MAX_PARTS);
            if (partNumberMarker != null) {
                requestBuilder.partNumberMarker(partNumberMarker);
            }

            ListPartsResponse response = client.listParts(requestBuilder.build()).join();
            response.parts().forEach(part -> {
                    ChunkPartInfo info = new ChunkPartInfo();
                    info.setPartNumber(part.partNumber());
                    info.setEtag(part.eTag());
                    info.setSize(part.size());
                    partInfos.add(info);
                });

            hasNextPage = Boolean.TRUE.equals(response.isTruncated());
            partNumberMarker = response.nextPartNumberMarker();
            if (hasNextPage && partNumberMarker == null) {
                throw new OssException("LIST_PARTS_PAGINATION_ERROR", "List parts response is truncated but missing next part marker");
            }
        } while (hasNextPage);
        return partInfos;
    }

    // ----------------------------------------------------------------
    // 私有工具
    // ----------------------------------------------------------------

}


