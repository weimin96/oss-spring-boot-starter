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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 上传操作
 *
 * @author panwm
 */
@Slf4j
public class PutOperations extends Operations implements OssPutService {

    private static final int LIST_PARTS_MAX_PARTS = 1000;
    private static final int MAX_COPY_PARTS = 10000;
    private static final int MAX_COPY_CONCURRENCY = 8;
    private static final long SINGLE_COPY_MAX_BYTES = 5_000_000_000L;
    private static final long SINGLE_PUT_MAX_BYTES = 5_000_000_000L;
    private static final long MIN_COPY_PART_SIZE = 5L * 1024 * 1024;
    private static final long MAX_COPY_PART_SIZE = 5L * 1024 * 1024 * 1024;
    private static final long MAX_COPY_OBJECT_BYTES = MAX_COPY_PART_SIZE * MAX_COPY_PARTS;
    private static final long COPY_PART_SIZE_ALIGNMENT = 1024L * 1024;
    private static final String MOVE_STAGING_PREFIX = ".oss-staging/move/";

    private final S3AsyncClient singlePartClient;

    /**
     * 创建上传操作门面。
     *
     * @param ossProperties   OSS 配置
     * @param client          S3 异步客户端
     * @param transferManager 传输管理器
     */
    public PutOperations(OssClientOptions ossProperties, S3AsyncClient client, S3TransferManager transferManager) {
        this(ossProperties, client, client, transferManager);
    }

    /**
     * 创建同时支持 multipart 与单请求上传的操作门面。
     *
     * @param ossProperties    OSS 配置
     * @param client           multipart 客户端
     * @param singlePartClient 未启用 multipart 的单请求客户端
     * @param transferManager  传输管理器
     */
    public PutOperations(OssClientOptions ossProperties, S3AsyncClient client,
                         S3AsyncClient singlePartClient, S3TransferManager transferManager) {
        super(ossProperties, client, transferManager);
        this.singlePartClient = singlePartClient == null ? client : singlePartClient;
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
        return putObjectForKey(bucketName, Util.normalizeObjectPrefix(path) + filename, in);
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
        // 预计算的是完整对象 SHA-256，不能随自动 multipart 上传作为 full-object checksum 发送。
        if (command.contentLength() != null
                && command.contentLength() <= (long) ossProperties.getMultipartThresholdInMb() * 1024 * 1024
                && !Util.isBlank(command.checksumSha256())) {
            requestBuilder.checksumSHA256(command.checksumSha256());
        }
        if (command.createOnly()) {
            requestBuilder.ifNoneMatch("*");
        }
        PutObjectRequest putRequest = requestBuilder.build();
        long fileSize;
        PutObjectResponse response;
        try {
            if (shouldUseSinglePartUpload(command)) {
                CompletableFuture<PutObjectResponse> completion = singlePartClient.putObject(putRequest, body);
                try {
                    fileSize = body.writeInputStream(command.input());
                } catch (RuntimeException streamFailure) {
                    body.cancel();
                    throw resolveCompletionFailure(completion, streamFailure);
                }
                response = completion.join();
                if (response == null) {
                    throw new OssException("OBJECT_UPLOAD_FAILED", "上传响应为空：" + key);
                }
            } else {
                UploadRequest uploadReq = UploadRequest.builder()
                        .requestBody(body)
                        .putObjectRequest(putRequest)
                        .build();
                Upload upload = transferManager.upload(uploadReq);
                try {
                    fileSize = body.writeInputStream(command.input());
                } catch (RuntimeException streamFailure) {
                    body.cancel();
                    throw resolveUploadFailure(upload, streamFailure);
                }
                CompletedUpload completedUpload = upload.completionFuture().join();
                if (completedUpload == null || completedUpload.response() == null) {
                    throw new OssException("OBJECT_UPLOAD_FAILED", "上传响应为空：" + key);
                }
                response = completedUpload.response();
            }
        } catch (RuntimeException e) {
            body.cancel();
            if (e instanceof OssException) {
                throw e;
            }
            Throwable cause = unwrapAsyncFailure(e);
            throw OssException.uploadFailed(key, cause);
        }

        return new StoredObject(bucket, key, fileSize, response.eTag(),
                response.versionId(), response.checksumSHA256());
    }

    private boolean shouldUseSinglePartUpload(PutObjectCommand command) {
        // 预计算 checksum 的上传绕过 TransferManager，避免兼容服务将 full-object checksum
        // 重新解释为 multipart checksum；超过单请求上限时仍回退到无预计算 checksum 的 multipart。
        return !Util.isBlank(command.checksumSha256())
                && command.contentLength() != null
                && command.contentLength() <= SINGLE_PUT_MAX_BYTES;
    }

    private RuntimeException resolveUploadFailure(Upload upload, RuntimeException streamFailure) {
        return resolveCompletionFailure(upload.completionFuture(), streamFailure);
    }

    private RuntimeException resolveCompletionFailure(
            CompletableFuture<?> completion, RuntimeException streamFailure) {
        try {
            completion.join();
            return streamFailure;
        } catch (RuntimeException completionFailure) {
            Throwable cause = unwrapAsyncFailure(completionFailure);
            if (cause != streamFailure) {
                cause.addSuppressed(streamFailure);
            }
            return cause instanceof RuntimeException
                    ? (RuntimeException) cause
                    : completionFailure;
        }
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
        return putObjectForKey(bucketName, Util.normalizeObjectPrefix(path) + filename, file);
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
        String directoryKey = Util.normalizeObjectPrefix(path);
        if (Util.isBlank(directoryKey)) {
            throw new IllegalArgumentException("目录路径不能为空");
        }
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucketName).key(directoryKey).build();
        requireSuccessfulRequest(() -> client.putObject(req, AsyncRequestBody.empty()),
                "DIRECTORY_CREATE_FAILED",
                "创建目录失败：" + directoryKey);
        return buildObjectInfo(directoryKey, new Date(), 0);
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
        path = Util.normalizeObjectPrefix(path);
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
        copyObject(new CopyObjectCommand(null, sourceKey, null, destKey));
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
        copyObject(new CopyObjectCommand(sourceBucket, sourceKey, destBucket, destKey));
    }

    @Override
    public StoredObject copyObject(CopyObjectCommand command) {
        CopyTarget target = validateCopyCommand(command);
        long startedAt = System.nanoTime();
        try {
            HeadObjectResponse source = headCopyObject(target.sourceBucket, target.sourceKey,
                    target.sourceVersionId, false,
                    "OBJECT_COPY_SOURCE_HEAD_FAILED", "读取复制源对象失败：");
            long sourceSize = requireCopyObjectSize(source, target.sourceKey);
            CopyExecutionPlan plan = executeCopy(target, source, sourceSize);

            HeadObjectResponse destination = headCopyObject(target.destinationBucket, target.destinationKey,
                    null, false,
                    "OBJECT_COPY_COMPLETED_VERIFY_FAILED", "复制已完成，但读取复制结果失败：");
            long destinationSize = requireCopyObjectSize(destination, target.destinationKey);
            if (sourceSize != destinationSize) {
                throw new OssException("OBJECT_COPY_COMPLETED_VERIFY_FAILED",
                        "复制已完成，但复制结果大小校验失败：" + target.destinationKey);
            }
            StoredObject result = buildStoredObject(target.destinationBucket, target.destinationKey, destination);
            log.info("OSS server-side copy completed: source=[{}/{}], destination=[{}/{}], size={}, "
                            + "strategy={}, parts={}, partSize={}, concurrency={}, elapsedMs={}",
                    target.sourceBucket, target.sourceKey,
                    target.destinationBucket, target.destinationKey,
                    sourceSize, plan.strategy, plan.partCount, plan.partSize,
                    plan.concurrency, elapsedMillis(startedAt));
            return result;
        } catch (RuntimeException failure) {
            log.warn("OSS server-side copy failed: source=[{}/{}], destination=[{}/{}], "
                            + "errorCode={}, elapsedMs={}",
                    target.sourceBucket, target.sourceKey,
                    target.destinationBucket, target.destinationKey,
                    copyFailureCode(failure), elapsedMillis(startedAt), failure);
            throw failure;
        }
    }

    private CopyTarget validateCopyCommand(CopyObjectCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("复制命令不能为空");
        }
        if (Util.isBlank(command.sourceKey())) {
            throw new IllegalArgumentException("源对象 key 不能为空");
        }
        if (Util.isBlank(command.destinationKey())) {
            throw new IllegalArgumentException("目标对象 key 不能为空");
        }
        String sourceBucket = Util.isBlank(command.sourceBucket())
                ? ossProperties.getBucketName() : command.sourceBucket();
        String destinationBucket = Util.isBlank(command.destinationBucket())
                ? ossProperties.getBucketName() : command.destinationBucket();
        if (Util.isBlank(sourceBucket) || Util.isBlank(destinationBucket)) {
            throw new IllegalArgumentException("源 Bucket 和目标 Bucket 不能为空");
        }
        String sourceKey = Util.normalizeObjectKey(command.sourceKey());
        String destinationKey = Util.normalizeObjectKey(command.destinationKey());
        String sourceVersionId = Util.isBlank(command.sourceVersionId())
                ? null : command.sourceVersionId();
        if (sourceBucket.equals(destinationBucket)
                && sourceKey.equals(destinationKey)
                && sourceVersionId == null) {
            throw new IllegalArgumentException("源对象和目标对象不能相同");
        }
        return new CopyTarget(sourceBucket, sourceKey, destinationBucket, destinationKey, sourceVersionId);
    }

    private CopyExecutionPlan executeCopy(CopyTarget target, HeadObjectResponse source, long sourceSize) {
        requireCopySourceIdentity(target, source);
        CopyExecutionPlan plan = createCopyExecutionPlan(sourceSize);
        log.debug("OSS server-side copy planned: source=[{}/{}], destination=[{}/{}], size={}, "
                        + "strategy={}, parts={}, partSize={}, concurrency={}",
                target.sourceBucket, target.sourceKey,
                target.destinationBucket, target.destinationKey,
                sourceSize, plan.strategy, plan.partCount, plan.partSize, plan.concurrency);
        if (plan.multipart) {
            copyMultipartObject(target, source, sourceSize, plan.partSize);
        } else {
            copySingleObject(target, source);
        }
        return plan;
    }

    private void copySingleObject(CopyTarget target, HeadObjectResponse source) {
        CopyObjectRequest.Builder request = CopyObjectRequest.builder()
                .sourceBucket(target.sourceBucket)
                .sourceKey(target.sourceKey)
                .destinationBucket(target.destinationBucket)
                .destinationKey(target.destinationKey);
        applyCopySourceCondition(request, target, source);
        executeCopyRequest(() -> client.copyObject(request.build()),
                "OBJECT_COPY_FAILED", "复制对象失败：" + target.sourceKey);
    }

    private void copyMultipartObject(CopyTarget target, HeadObjectResponse source,
                                     long sourceSize, long partSize) {
        Tagging tagging = loadSourceTagging(target, source);
        CreateMultipartUploadRequest.Builder createRequest = CreateMultipartUploadRequest.builder()
                .bucket(target.destinationBucket)
                .key(target.destinationKey)
                .cacheControl(source.cacheControl())
                .contentDisposition(source.contentDisposition())
                .contentEncoding(source.contentEncoding())
                .contentLanguage(source.contentLanguage())
                .contentType(source.contentType())
                .expires(source.expires())
                .metadata(source.metadata())
                .websiteRedirectLocation(source.websiteRedirectLocation());
        if (tagging != null) {
            createRequest.tagging(tagging);
        }
        if (source.storageClass() != null && source.storageClass() != StorageClass.UNKNOWN_TO_SDK_VERSION) {
            createRequest.storageClass(source.storageClass());
        }

        CreateMultipartUploadResponse created = executeCopyRequest(
                () -> client.createMultipartUpload(createRequest.build()),
                "OBJECT_MULTIPART_COPY_INIT_FAILED",
                "初始化分片复制失败：" + target.destinationKey);
        if (Util.isBlank(created.uploadId())) {
            throw new OssException("OBJECT_MULTIPART_COPY_INIT_FAILED",
                    "初始化分片复制未返回 uploadId：" + target.destinationKey);
        }

        String uploadId = created.uploadId();
        boolean completed = false;
        try {
            List<CompletedPart> completedParts = copyMultipartParts(
                    target, source, sourceSize, partSize, uploadId);
            CompletedMultipartUpload multipartUpload = CompletedMultipartUpload.builder()
                    .parts(completedParts)
                    .build();
            executeCopyRequest(() -> client.completeMultipartUpload(
                            CompleteMultipartUploadRequest.builder()
                                    .bucket(target.destinationBucket)
                                    .key(target.destinationKey)
                                    .uploadId(uploadId)
                                    .multipartUpload(multipartUpload)
                                    .build()),
                    "OBJECT_MULTIPART_COPY_COMPLETE_FAILED",
                    "完成分片复制失败：" + target.destinationKey);
            completed = true;
        } catch (RuntimeException failure) {
            if (!completed) {
                abortMultipartCopy(target, uploadId, failure);
            }
            throw failure;
        }
    }

    private List<CompletedPart> copyMultipartParts(CopyTarget target, HeadObjectResponse source,
                                                   long sourceSize, long partSize, String uploadId) {
        List<CompletedPart> completedParts = new ArrayList<>();
        int concurrency = calculateCopyConcurrency();
        long offset = 0;
        int partNumber = 1;
        while (offset < sourceSize) {
            List<CompletableFuture<CompletedPart>> batch = new ArrayList<>(concurrency);
            while (offset < sourceSize && batch.size() < concurrency) {
                long currentPartSize = Math.min(partSize, sourceSize - offset);
                long end = offset + currentPartSize - 1;
                int currentPartNumber = partNumber;
                UploadPartCopyRequest.Builder request = UploadPartCopyRequest.builder()
                        .sourceBucket(target.sourceBucket)
                        .sourceKey(target.sourceKey)
                        .destinationBucket(target.destinationBucket)
                        .destinationKey(target.destinationKey)
                        .uploadId(uploadId)
                        .partNumber(currentPartNumber)
                        .copySourceRange("bytes=" + offset + "-" + end);
                applyCopySourceCondition(request, target, source);
                batch.add(startCopyPart(request.build(), target.sourceKey, currentPartNumber));
                offset = end + 1;
                partNumber++;
            }
            awaitCopyPartBatch(batch, target.sourceKey);
            for (CompletableFuture<CompletedPart> future : batch) {
                completedParts.add(future.join());
            }
        }
        return completedParts;
    }

    private CompletableFuture<CompletedPart> startCopyPart(UploadPartCopyRequest request,
                                                            String sourceKey, int partNumber) {
        CompletableFuture<UploadPartCopyResponse> responseFuture;
        try {
            responseFuture = client.uploadPartCopy(request);
        } catch (RuntimeException failure) {
            responseFuture = failedFuture(failure);
        }
        if (responseFuture == null) {
            responseFuture = failedFuture(new OssException("OBJECT_MULTIPART_COPY_PART_FAILED",
                    "复制对象分片未返回异步结果：" + sourceKey + "，part=" + partNumber));
        }
        return responseFuture.thenApply(response -> buildCompletedCopyPart(response, sourceKey, partNumber));
    }

    private CompletedPart buildCompletedCopyPart(UploadPartCopyResponse response,
                                                  String sourceKey, int partNumber) {
        CopyPartResult partResult = response == null ? null : response.copyPartResult();
        if (partResult == null || Util.isBlank(partResult.eTag())) {
            throw new OssException("OBJECT_MULTIPART_COPY_PART_FAILED",
                    "复制对象分片未返回 ETag：" + sourceKey + "，part=" + partNumber);
        }
        return CompletedPart.builder()
                .partNumber(partNumber)
                .eTag(partResult.eTag())
                .build();
    }

    private void awaitCopyPartBatch(List<CompletableFuture<CompletedPart>> batch, String sourceKey) {
        CompletableFuture<?>[] futures = batch.toArray(new CompletableFuture<?>[batch.size()]);
        CompletableFuture<Void> batchFuture = CompletableFuture.allOf(futures);
        boolean interrupted = false;
        InterruptedException interruptionCause = null;
        ExecutionException executionFailure = null;
        while (true) {
            try {
                batchFuture.get();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
                if (interruptionCause == null) {
                    interruptionCause = e;
                }
            } catch (ExecutionException e) {
                executionFailure = e;
                break;
            }
        }

        OssException batchFailure = executionFailure == null
                ? null : collectCopyPartFailures(batch, sourceKey, executionFailure.getCause());
        if (interrupted) {
            Thread.currentThread().interrupt();
            OssException interruption = new OssException("OSS_INTERRUPTED",
                    "复制对象分片时线程被中断：" + sourceKey, interruptionCause);
            if (batchFailure != null) {
                interruption.addSuppressed(batchFailure);
            }
            throw interruption;
        }
        if (batchFailure != null) {
            throw batchFailure;
        }
    }

    private OssException collectCopyPartFailures(List<CompletableFuture<CompletedPart>> batch,
                                                  String sourceKey, Throwable fallbackFailure) {
        OssException primary = null;
        for (CompletableFuture<CompletedPart> future : batch) {
            if (!future.isCompletedExceptionally()) {
                continue;
            }
            try {
                future.join();
            } catch (RuntimeException failure) {
                Throwable cause = unwrapAsyncFailure(failure);
                OssException mapped = cause instanceof OssException
                        ? (OssException) cause
                        : mapCopyFailure("OBJECT_MULTIPART_COPY_PART_FAILED",
                        "复制对象分片失败：" + sourceKey, cause);
                if (primary == null) {
                    primary = mapped;
                } else if (primary != mapped) {
                    primary.addSuppressed(mapped);
                }
            }
        }
        return primary == null
                ? mapCopyFailure("OBJECT_MULTIPART_COPY_PART_FAILED",
                "复制对象分片失败：" + sourceKey, fallbackFailure)
                : primary;
    }

    private Throwable unwrapAsyncFailure(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private <T> CompletableFuture<T> failedFuture(Throwable failure) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(failure);
        return future;
    }

    private int calculateCopyConcurrency() {
        return Math.max(1, Math.min(MAX_COPY_CONCURRENCY, ossProperties.getMaxConnections()));
    }

    private HeadObjectResponse headCopyObject(String bucket, String key, String versionId,
                                              boolean checksumEnabled,
                                              String errorCode, String messagePrefix) {
        HeadObjectRequest.Builder request = HeadObjectRequest.builder()
                .bucket(bucket)
                .key(key);
        if (!Util.isBlank(versionId)) {
            request.versionId(versionId);
        }
        if (checksumEnabled) {
            request.checksumMode(ChecksumMode.ENABLED);
        }
        return executeCopyRequest(() -> client.headObject(request.build()),
                errorCode, messagePrefix + key);
    }

    private Tagging loadSourceTagging(CopyTarget target, HeadObjectResponse source) {
        GetObjectTaggingRequest.Builder request = GetObjectTaggingRequest.builder()
                .bucket(target.sourceBucket)
                .key(target.sourceKey);
        String sourceVersionId = resolveCopySourceVersion(target, source);
        if (!Util.isBlank(sourceVersionId)) {
            request.versionId(sourceVersionId);
        }
        GetObjectTaggingResponse response = executeCopyRequest(() -> client.getObjectTagging(
                        request.build()),
                "OBJECT_MULTIPART_COPY_TAGGING_FAILED",
                "读取源对象标签失败：" + target.sourceKey);
        if (response.tagSet().isEmpty()) {
            return null;
        }
        return Tagging.builder().tagSet(response.tagSet()).build();
    }

    private CopyExecutionPlan createCopyExecutionPlan(long objectSize) {
        if (objectSize <= SINGLE_COPY_MAX_BYTES) {
            return new CopyExecutionPlan(false, "CopyObject", 0L, 1, 1);
        }
        long partSize = calculateCopyPartSize(objectSize);
        long partCount = objectSize / partSize;
        if (objectSize % partSize != 0) {
            partCount++;
        }
        return new CopyExecutionPlan(true, "UploadPartCopy", partSize,
                (int) partCount, calculateCopyConcurrency());
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private String copyFailureCode(RuntimeException failure) {
        return failure instanceof OssException
                ? ((OssException) failure).getCode()
                : failure.getClass().getSimpleName();
    }

    private long calculateCopyPartSize(long objectSize) {
        if (objectSize > MAX_COPY_OBJECT_BYTES) {
            throw new OssException("OBJECT_COPY_TOO_LARGE", "对象大小超过 48.8 TiB：" + objectSize);
        }
        long minimumPartSize = objectSize / MAX_COPY_PARTS;
        if (objectSize % MAX_COPY_PARTS != 0) {
            minimumPartSize++;
        }
        long alignedPartSize = ((minimumPartSize + COPY_PART_SIZE_ALIGNMENT - 1)
                / COPY_PART_SIZE_ALIGNMENT) * COPY_PART_SIZE_ALIGNMENT;
        long configuredPartSize = (long) ossProperties.getPartSizeInMb() * 1024 * 1024;
        if (configuredPartSize < MIN_COPY_PART_SIZE || configuredPartSize > MAX_COPY_PART_SIZE) {
            throw OssException.configInvalid("part-size-in-mb");
        }
        long partSize = Math.max(configuredPartSize, alignedPartSize);
        if (partSize > MAX_COPY_PART_SIZE) {
            throw new OssException("OBJECT_COPY_TOO_LARGE", "对象大小超过分片复制支持范围");
        }
        return partSize;
    }

    private <T> T executeCopyRequest(Supplier<CompletableFuture<T>> requestSupplier,
                                     String defaultCode, String message) {
        try {
            T response = executeRequestStrict(requestSupplier);
            if (response == null) {
                throw new OssException(defaultCode, message);
            }
            return response;
        } catch (NoSuchKeyException e) {
            throw new OssException("OBJECT_NOT_FOUND", message, e);
        } catch (NoSuchBucketException e) {
            throw new OssException("BUCKET_NOT_FOUND", message, e);
        } catch (S3Exception e) {
            throw mapCopyFailure(defaultCode, message, e);
        } catch (OssException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new OssException(defaultCode, message, e);
        }
    }

    private OssException mapCopyFailure(String defaultCode, String message, Throwable failure) {
        Throwable cause = unwrapAsyncFailure(failure);
        if (cause instanceof NoSuchKeyException) {
            return new OssException("OBJECT_NOT_FOUND", message, cause);
        }
        if (cause instanceof NoSuchBucketException) {
            return new OssException("BUCKET_NOT_FOUND", message, cause);
        }
        if (!(cause instanceof S3Exception)) {
            return new OssException(defaultCode, message, cause);
        }
        S3Exception s3Exception = (S3Exception) cause;
        String errorCode = s3Exception.awsErrorDetails() == null
                ? null : s3Exception.awsErrorDetails().errorCode();
        if ("NoSuchKey".equals(errorCode)) {
            return new OssException("OBJECT_NOT_FOUND", message, s3Exception);
        }
        if ("NoSuchBucket".equals(errorCode)) {
            return new OssException("BUCKET_NOT_FOUND", message, s3Exception);
        }
        if (s3Exception.statusCode() == 403 || "AccessDenied".equals(errorCode)) {
            return new OssException("OBJECT_COPY_FORBIDDEN", message, s3Exception);
        }
        if (s3Exception.statusCode() == 412 || "PreconditionFailed".equals(errorCode)) {
            return new OssException("OBJECT_COPY_SOURCE_CHANGED", message, s3Exception);
        }
        if (s3Exception.statusCode() == 501 || "NotImplemented".equals(errorCode)) {
            return new OssException("OBJECT_COPY_UNSUPPORTED", message, s3Exception);
        }
        return new OssException(defaultCode, message, s3Exception);
    }

    private long requireCopyObjectSize(HeadObjectResponse response, String key) {
        if (response.contentLength() == null || response.contentLength() < 0) {
            throw new OssException("OBJECT_COPY_SIZE_INVALID", "对象大小无效：" + key);
        }
        return response.contentLength();
    }

    private void requireCopySourceIdentity(CopyTarget target, HeadObjectResponse source) {
        if (Util.isBlank(resolveCopySourceVersion(target, source)) && Util.isBlank(source.eTag())) {
            throw new OssException("OBJECT_COPY_SOURCE_IDENTITY_MISSING",
                    "源对象缺少 versionId 或 ETag，无法保证复制一致性：" + target.sourceKey);
        }
    }

    private void applyCopySourceCondition(CopyObjectRequest.Builder request,
                                          CopyTarget target, HeadObjectResponse source) {
        String sourceVersionId = resolveCopySourceVersion(target, source);
        if (!Util.isBlank(sourceVersionId)) {
            request.sourceVersionId(sourceVersionId);
        } else if (!Util.isBlank(source.eTag())) {
            request.copySourceIfMatch(source.eTag());
        }
    }

    private void applyCopySourceCondition(UploadPartCopyRequest.Builder request,
                                          CopyTarget target, HeadObjectResponse source) {
        String sourceVersionId = resolveCopySourceVersion(target, source);
        if (!Util.isBlank(sourceVersionId)) {
            request.sourceVersionId(sourceVersionId);
        } else if (!Util.isBlank(source.eTag())) {
            request.copySourceIfMatch(source.eTag());
        }
    }

    private String resolveCopySourceVersion(CopyTarget target, HeadObjectResponse source) {
        return !Util.isBlank(target.sourceVersionId)
                ? target.sourceVersionId : source.versionId();
    }

    private void abortMultipartCopy(CopyTarget target, String uploadId, RuntimeException failure) {
        String message = "中止分片复制失败：" + target.destinationKey;
        try {
            AbortMultipartUploadResponse response = executeRequestStrict(() -> client.abortMultipartUpload(
                    AbortMultipartUploadRequest.builder()
                            .bucket(target.destinationBucket)
                            .key(target.destinationKey)
                            .uploadId(uploadId)
                            .build()));
            if (response == null) {
                throw new OssException("OBJECT_MULTIPART_COPY_ABORT_FAILED", message);
            }
        } catch (RuntimeException abortCause) {
            failure.addSuppressed(new OssException(
                    "OBJECT_MULTIPART_COPY_ABORT_FAILED", message, abortCause));
        }
    }

    private static final class CopyExecutionPlan {
        private final boolean multipart;
        private final String strategy;
        private final long partSize;
        private final int partCount;
        private final int concurrency;

        private CopyExecutionPlan(boolean multipart, String strategy, long partSize,
                                  int partCount, int concurrency) {
            this.multipart = multipart;
            this.strategy = strategy;
            this.partSize = partSize;
            this.partCount = partCount;
            this.concurrency = concurrency;
        }
    }

    private static final class CopyTarget {
        private final String sourceBucket;
        private final String sourceKey;
        private final String destinationBucket;
        private final String destinationKey;
        private final String sourceVersionId;

        private CopyTarget(String sourceBucket, String sourceKey,
                           String destinationBucket, String destinationKey,
                           String sourceVersionId) {
            this.sourceBucket = sourceBucket;
            this.sourceKey = sourceKey;
            this.destinationBucket = destinationBucket;
            this.destinationKey = destinationKey;
            this.sourceVersionId = sourceVersionId;
        }
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
        String destKey = Util.normalizeObjectPrefix(destinationDirectory) + filename;
        destKey = Util.normalizeObjectKey(destKey);
        HeadObjectResponse sourceResponse = headMoveResponse(bucketName, sourceKey,
                "MOVE_SOURCE_HEAD_FAILED", "读取源对象失败：");
        StoredObject source = buildStoredObject(bucketName, sourceKey, sourceResponse);
        if (sourceKey.equals(destKey)) {
            return;
        }

        String stagingKey = MOVE_STAGING_PREFIX + UUID.randomUUID().toString();
        boolean stagingCleanupRequired = true;
        try {
            executeCopy(new CopyTarget(bucketName, sourceKey, bucketName, stagingKey, null),
                    sourceResponse, source.size());
            HeadObjectResponse stagingResponse = headMoveResponse(bucketName, stagingKey,
                    "MOVE_STAGING_HEAD_FAILED", "读取 staging 对象失败：");
            StoredObject staging = buildStoredObject(bucketName, stagingKey, stagingResponse);
            verifyMoveCopy(source, staging, "MOVE_STAGING_INVALID", "staging 对象校验失败：");

            executeCopy(new CopyTarget(bucketName, stagingKey, bucketName, destKey, null),
                    stagingResponse, staging.size());
            StoredObject destination = headMoveObject(bucketName, destKey,
                    "MOVE_DESTINATION_HEAD_FAILED", "读取最终对象失败：");
            verifyMoveCopy(source, destination, "MOVE_DESTINATION_INVALID", "最终对象校验失败：");

            deleteMoveObject(bucketName, stagingKey,
                    "MOVE_STAGING_DELETE_FAILED", "删除 staging 对象失败：");
            stagingCleanupRequired = false;
            deleteMoveSourceObject(bucketName, sourceKey, sourceResponse);
        } catch (RuntimeException failure) {
            if (stagingCleanupRequired) {
                cleanupMoveStaging(bucketName, stagingKey, failure);
            }
            throw failure;
        }
    }

    private StoredObject headMoveObject(String bucketName, String key, String errorCode, String messagePrefix) {
        return buildStoredObject(bucketName, key,
                headMoveResponse(bucketName, key, errorCode, messagePrefix));
    }

    private HeadObjectResponse headMoveResponse(String bucketName, String key,
                                                String errorCode, String messagePrefix) {
        return requireSuccessfulRequest(() -> client.headObject(HeadObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .checksumMode(ChecksumMode.ENABLED)
                        .build()),
                errorCode, messagePrefix + key);
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

    private void deleteMoveSourceObject(String bucketName, String sourceKey,
                                        HeadObjectResponse sourceResponse) {
        if (Util.isBlank(sourceResponse.eTag())) {
            throw new OssException("MOVE_SOURCE_IDENTITY_MISSING",
                    "源对象缺少 ETag，无法执行条件删除：" + sourceKey);
        }
        try {
            DeleteObjectResponse response = executeRequestStrict(() -> client.deleteObject(request -> request
                    .bucket(bucketName)
                    .key(sourceKey)
                    .ifMatch(sourceResponse.eTag())
                    .build()));
            if (response == null) {
                throw new OssException("MOVE_SOURCE_DELETE_FAILED", "删除源对象失败：" + sourceKey);
            }
        } catch (S3Exception exception) {
            String errorCode = exception.awsErrorDetails() == null
                    ? null : exception.awsErrorDetails().errorCode();
            if (exception.statusCode() == 412 || "PreconditionFailed".equals(errorCode)) {
                throw new OssException("MOVE_SOURCE_CHANGED",
                        "源对象在移动期间发生变化，已保留源对象：" + sourceKey, exception);
            }
            if (exception.statusCode() == 403 || "AccessDenied".equals(errorCode)) {
                throw new OssException("MOVE_SOURCE_DELETE_FORBIDDEN",
                        "没有源对象删除权限：" + sourceKey, exception);
            }
            if (exception.statusCode() == 501 || "NotImplemented".equals(errorCode)) {
                throw new OssException("MOVE_CONDITIONAL_DELETE_UNSUPPORTED",
                        "当前存储服务不支持安全的条件删除，已保留源对象：" + sourceKey, exception);
            }
            throw new OssException("MOVE_SOURCE_DELETE_FAILED",
                    "删除源对象失败：" + sourceKey, exception);
        } catch (OssException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OssException("MOVE_SOURCE_DELETE_FAILED",
                    "删除源对象失败：" + sourceKey, exception);
        }
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
        if (chunkTask == null) {
            throw new IllegalArgumentException("分片任务不能为空");
        }
        String objectName = buildChunkObjectName(chunkTask.getPath(), chunkTask.getFilename());
        CreateMultipartUploadResponse response = executeRequestStrict(() ->
                client.createMultipartUpload(CreateMultipartUploadRequest.builder()
                        .bucket(ossProperties.getBucketName())
                        .key(objectName)
                        .build()));
        if (response == null || Util.isBlank(response.uploadId())) {
            throw new OssException("MULTIPART_INIT_FAILED",
                    "初始化分片上传未返回 uploadId：" + objectName);
        }
        return response.uploadId();
    }

    /**
     * 上传单个分片。
     *
     * @param chunk 分片内容和分片元数据
     * @return 分片号与 ETag 信息
     */
    @Override
    public ChunkTarget chunk(ChunkUploadCommand chunk) {
        validateChunkUploadCommand(chunk);
        String objectName = buildChunkObjectName(chunk.getPath(), chunk.getFilename());
        UploadPartRequest request = UploadPartRequest.builder()
                .bucket(ossProperties.getBucketName())
                .key(objectName)
                .uploadId(chunk.getUploadId())
                .partNumber(chunk.getChunkNumber())
                .contentLength(chunk.getContentLength())
                .build();
        try {
            ByteBuffer buffer = ByteBuffer.wrap(chunk.getFileBytes());
            UploadPartResponse response = executeRequestStrict(() ->
                    client.uploadPart(request, AsyncRequestBody.fromByteBuffer(buffer)));
            if (response == null || Util.isBlank(response.eTag())) {
                throw new OssException("MULTIPART_PART_UPLOAD_FAILED",
                        "分片上传未返回 ETag：" + objectName + "，part=" + chunk.getChunkNumber());
            }
            ChunkTarget target = new ChunkTarget();
            target.setEtag(normalizeEtag(response.eTag()));
            target.setPartNumber(chunk.getChunkNumber());
            return target;
        } catch (RuntimeException exception) {
            log.error("分片上传失败 file={} part={}", chunk.getFilename(), chunk.getChunkNumber(), exception);
            if (exception instanceof OssException) {
                throw exception;
            }
            throw OssException.uploadFailed(chunk.getFilename(), exception);
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
        validateChunkMergeCommand(chunkMerge);
        String bucketName = ossProperties.getBucketName();
        String objectName = buildChunkObjectName(chunkMerge.getPath(), chunkMerge.getFilename());
        List<CompletedPart> parts = validateMultipartMergeParts(
                bucketName, objectName, chunkMerge);

        CompleteMultipartUploadResponse completed = executeRequestStrict(() ->
                client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                        .bucket(bucketName)
                        .key(objectName)
                        .uploadId(chunkMerge.getUploadId())
                        .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())
                        .build()));
        if (completed == null) {
            throw new OssException("MULTIPART_MERGE_FAILED", "合并分片失败：" + objectName);
        }

        HeadObjectResponse response;
        try {
            response = executeRequestStrict(() -> client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build()));
        } catch (RuntimeException exception) {
            throw new OssException("MULTIPART_MERGE_COMPLETED_VERIFY_FAILED",
                    "分片合并已完成，但读取结果失败：" + objectName, exception);
        }
        if (response == null || response.contentLength() == null
                || response.contentLength().longValue() != chunkMerge.getExpectedSize().longValue()) {
            throw new OssException("MULTIPART_MERGE_COMPLETED_VERIFY_FAILED",
                    "分片合并已完成，但结果大小校验失败：" + objectName);
        }
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
        if (Util.isBlank(bucketName)) {
            throw new IllegalArgumentException("Bucket 名称不能为空");
        }
        if (Util.isBlank(objectName)) {
            throw new IllegalArgumentException("对象 key 不能为空");
        }
        if (Util.isBlank(uploadId)) {
            throw new IllegalArgumentException("uploadId 不能为空");
        }
        String objectKey = Util.normalizeObjectKey(objectName);
        List<ChunkPartInfo> partInfos = new ArrayList<>();
        Integer partNumberMarker = null;
        boolean hasNextPage;
        do {
            ListPartsRequest.Builder requestBuilder = ListPartsRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .uploadId(uploadId)
                    .maxParts(LIST_PARTS_MAX_PARTS);
            if (partNumberMarker != null) {
                requestBuilder.partNumberMarker(partNumberMarker);
            }

            ListPartsResponse response = executeRequestStrict(() ->
                    client.listParts(requestBuilder.build()));
            if (response == null) {
                throw new OssException("LIST_PARTS_FAILED", "查询分片列表失败：" + objectKey);
            }
            response.parts().forEach(part -> {
                ChunkPartInfo info = new ChunkPartInfo();
                info.setPartNumber(part.partNumber());
                info.setEtag(normalizeEtag(part.eTag()));
                info.setSize(part.size());
                partInfos.add(info);
            });

            hasNextPage = Boolean.TRUE.equals(response.isTruncated());
            partNumberMarker = response.nextPartNumberMarker();
            if (hasNextPage && partNumberMarker == null) {
                throw new OssException("LIST_PARTS_PAGINATION_ERROR", "List parts response is truncated but missing next part marker");
            }
        } while (hasNextPage);
        partInfos.sort(Comparator.comparingInt(ChunkPartInfo::getPartNumber));
        return partInfos;
    }

    private String buildChunkObjectName(String path, String filename) {
        if (Util.isBlank(filename)) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String objectName = Util.normalizeObjectKey(
                Util.normalizeObjectPrefix(path) + filename);
        if (Util.isBlank(objectName)) {
            throw new IllegalArgumentException("对象 key 不能为空");
        }
        return objectName;
    }

    private void validateChunkUploadCommand(ChunkUploadCommand chunk) {
        if (chunk == null) {
            throw new IllegalArgumentException("分片上传命令不能为空");
        }
        if (chunk.getChunkNumber() == null
                || chunk.getChunkNumber() < 1
                || chunk.getChunkNumber() > MAX_COPY_PARTS) {
            throw new IllegalArgumentException("分片编号必须位于 1 到 10000 之间");
        }
        if (Util.isBlank(chunk.getUploadId())) {
            throw new IllegalArgumentException("uploadId 不能为空");
        }
        if (chunk.getFileBytes() == null || chunk.getFileBytes().length == 0) {
            throw new IllegalArgumentException("分片内容不能为空");
        }
        if (chunk.getContentLength() != chunk.getFileBytes().length) {
            throw new IllegalArgumentException("分片内容长度与实际字节数不一致");
        }
        buildChunkObjectName(chunk.getPath(), chunk.getFilename());
    }

    private void validateChunkMergeCommand(ChunkMerge chunkMerge) {
        if (chunkMerge == null) {
            throw new IllegalArgumentException("分片合并命令不能为空");
        }
        if (Util.isBlank(chunkMerge.getUploadId())) {
            throw new IllegalArgumentException("uploadId 不能为空");
        }
        if (chunkMerge.getExpectedPartCount() == null
                || chunkMerge.getExpectedPartCount() < 1
                || chunkMerge.getExpectedPartCount() > MAX_COPY_PARTS) {
            throw new IllegalArgumentException("expectedPartCount 必须位于 1 到 10000 之间");
        }
        if (chunkMerge.getExpectedSize() == null || chunkMerge.getExpectedSize() <= 0) {
            throw new IllegalArgumentException("expectedSize 必须大于 0");
        }
        if (chunkMerge.getChunkTargetList() == null || chunkMerge.getChunkTargetList().isEmpty()) {
            throw new IllegalArgumentException("分片列表不能为空，请确保所有分片已上传完成");
        }
        if (chunkMerge.getChunkTargetList().size() != chunkMerge.getExpectedPartCount()) {
            throw new OssException("MULTIPART_PART_COUNT_MISMATCH",
                    "客户端分片数量与 expectedPartCount 不一致");
        }
        buildChunkObjectName(chunkMerge.getPath(), chunkMerge.getFilename());
    }

    private List<CompletedPart> validateMultipartMergeParts(String bucketName, String objectName,
                                                             ChunkMerge chunkMerge) {
        List<ChunkTarget> requestedParts = new ArrayList<>(chunkMerge.getChunkTargetList());
        requestedParts.sort(Comparator.comparingInt(target -> {
            if (target == null || target.getPartNumber() == null) {
                return Integer.MAX_VALUE;
            }
            return target.getPartNumber();
        }));
        List<ChunkPartInfo> uploadedParts = listParts(
                bucketName, objectName, chunkMerge.getUploadId());
        if (uploadedParts.size() != chunkMerge.getExpectedPartCount()) {
            throw new OssException("MULTIPART_PART_COUNT_MISMATCH",
                    "服务端已上传分片数量与 expectedPartCount 不一致：" + objectName);
        }

        long uploadedSize = 0L;
        List<CompletedPart> completedParts = new ArrayList<>(uploadedParts.size());
        for (int index = 0; index < uploadedParts.size(); index++) {
            int expectedPartNumber = index + 1;
            ChunkTarget requested = requestedParts.get(index);
            ChunkPartInfo uploaded = uploadedParts.get(index);
            if (requested == null || requested.getPartNumber() == null
                    || requested.getPartNumber() != expectedPartNumber
                    || uploaded.getPartNumber() == null
                    || uploaded.getPartNumber() != expectedPartNumber) {
                throw new OssException("MULTIPART_PART_SEQUENCE_INVALID",
                        "分片编号必须从 1 开始连续排列：" + objectName);
            }
            String requestedEtag = normalizeEtag(requested.getEtag());
            String uploadedEtag = normalizeEtag(uploaded.getEtag());
            if (Util.isBlank(requestedEtag) || !requestedEtag.equals(uploadedEtag)) {
                throw new OssException("MULTIPART_PART_ETAG_MISMATCH",
                        "分片 ETag 与服务端记录不一致：" + objectName
                                + "，part=" + expectedPartNumber);
            }
            if (uploaded.getSize() == null || uploaded.getSize() <= 0) {
                throw new OssException("MULTIPART_PART_SIZE_INVALID",
                        "服务端分片大小无效：" + objectName
                                + "，part=" + expectedPartNumber);
            }
            try {
                uploadedSize = Math.addExact(uploadedSize, uploaded.getSize());
            } catch (ArithmeticException exception) {
                throw new OssException("MULTIPART_SIZE_OVERFLOW",
                        "分片总大小超出 long 范围：" + objectName, exception);
            }
            completedParts.add(CompletedPart.builder()
                    .partNumber(expectedPartNumber)
                    .eTag(uploaded.getEtag())
                    .build());
        }
        if (uploadedSize != chunkMerge.getExpectedSize()) {
            throw new OssException("MULTIPART_SIZE_MISMATCH",
                    "服务端分片总大小与 expectedSize 不一致：" + objectName);
        }
        return completedParts;
    }

    private String normalizeEtag(String etag) {
        return etag == null ? null : etag.trim().replace("\"", "");
    }

    // ----------------------------------------------------------------
    // 私有工具
    // ----------------------------------------------------------------

}


