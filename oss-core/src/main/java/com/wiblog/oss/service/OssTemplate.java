package com.wiblog.oss.service;

import com.wiblog.oss.config.OssClientOptions;
import com.wiblog.oss.exception.OssException;
import com.wiblog.oss.util.Util;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.multipart.MultipartConfiguration;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * OSS 操作入口。
 *
 * @author panwm
 */
@Slf4j
public class OssTemplate {

    private final OssClientOptions ossProperties;

    // volatile 保证 stop/restart 场景下多线程可见性。
    private volatile S3AsyncClient client;
    private volatile S3TransferManager transferManager;
    private volatile PutOperations putOperations;
    private volatile QueryOperations queryOperations;
    private volatile DeleteOperations deleteOperations;
    private volatile StreamUnzipOperations streamUnzipOperations;
    private volatile PresignOperations presignOperations;
    private volatile TaggingOperations taggingOperations;
    private volatile BucketOperations bucketOperations;

    /**
     * 创建并立即启动一个 OSS 门面对象。
     *
     * <p>构造后直接调用 {@link #start()} 的原因是该类型面向工具包调用方时应尽量做到开箱即用，
     * 避免出现“实例已创建但内部客户端尚未初始化”的半成品状态。</p>
     *
     * @param ossProperties OSS 配置
     */
    public OssTemplate(OssClientOptions ossProperties) {
        this.ossProperties = ossProperties;
        this.start();
    }

    // ----------------------------------------------------------------
    // 生命周期
    // ----------------------------------------------------------------

    /**
     * 启动或重建底层 S3 客户端及各操作门面。
     *
     * <p>该方法使用同步锁保护，是为了保证 stop/restart 场景下不会出现部分组件已经替换、
     * 部分组件仍引用旧客户端的竞态状态。</p>
     */
    public synchronized void start() {
        if (this.client != null || this.transferManager != null || this.presignOperations != null) {
            stop();
        }

        S3AsyncClient newClient = buildClient(ossProperties);
        S3TransferManager newTransferManager = null;
        PresignOperations newPresignOperations = null;
        try {
            newTransferManager = S3TransferManager.builder().s3Client(newClient).build();
            ensureBucketExists(newClient);
            PutOperations newPutOperations = new PutOperations(ossProperties, newClient, newTransferManager);
            QueryOperations newQueryOperations = new QueryOperations(ossProperties, newClient, newTransferManager);
            DeleteOperations newDeleteOperations = new DeleteOperations(ossProperties, newClient, newTransferManager);
            StreamUnzipOperations newStreamUnzipOperations =
                    new StreamUnzipOperations(ossProperties, newClient, newTransferManager);
            newPresignOperations = new PresignOperations(ossProperties, newClient, newTransferManager);
            TaggingOperations newTaggingOperations = new TaggingOperations(ossProperties, newClient, newTransferManager);
            BucketOperations newBucketOperations = new BucketOperations(ossProperties, newClient, newTransferManager);

            this.client = newClient;
            this.transferManager = newTransferManager;
            this.putOperations = newPutOperations;
            this.queryOperations = newQueryOperations;
            this.deleteOperations = newDeleteOperations;
            this.streamUnzipOperations = newStreamUnzipOperations;
            this.presignOperations = newPresignOperations;
            this.taggingOperations = newTaggingOperations;
            this.bucketOperations = newBucketOperations;
        } catch (RuntimeException exception) {
            try {
                closeAll(newPresignOperations, newTransferManager, newClient);
            } catch (RuntimeException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
        log.info("OSS initialized - endpoint={}, bucket={}, type={}",
                ossProperties.getEndpoint(), ossProperties.getBucketName(), ossProperties.getType());
    }

    /**
     * 关闭底层客户端和附属资源。
     *
     * <p>这里按“预签名器 -> 传输管理器 -> S3 客户端”的顺序关闭，
     * 是为了先释放上层依赖，再释放底层连接资源，避免后续清理过程访问到已关闭的客户端。</p>
     */
    public synchronized void stop() {
        PresignOperations currentPresignOperations = this.presignOperations;
        S3TransferManager currentTransferManager = this.transferManager;
        S3AsyncClient currentClient = this.client;
        this.presignOperations = null;
        this.transferManager = null;
        this.client = null;
        this.putOperations = null;
        this.queryOperations = null;
        this.deleteOperations = null;
        this.streamUnzipOperations = null;
        this.taggingOperations = null;
        this.bucketOperations = null;
        closeAll(currentPresignOperations, currentTransferManager, currentClient);
        log.info("OSS client closed");
    }

    static void closeAll(AutoCloseable... resources) {
        RuntimeException firstFailure = null;
        for (AutoCloseable resource : resources) {
            if (resource == null) {
                continue;
            }
            try {
                resource.close();
            } catch (Exception exception) {
                RuntimeException failure = exception instanceof RuntimeException
                        ? (RuntimeException) exception
                        : new OssException("OSS_CLOSE_FAILED", "关闭 OSS 资源失败", exception);
                if (firstFailure == null) {
                    firstFailure = failure;
                } else {
                    firstFailure.addSuppressed(failure);
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    // ----------------------------------------------------------------
    // 门面方法
    // ----------------------------------------------------------------

    /**
     * 返回上传相关操作入口。
     *
     * @return 上传操作门面
     */
    public OssPutService put() {
        return putOperations;
    }

    /**
     * 返回查询相关操作入口。
     *
     * @return 查询操作门面
     */
    public OssQueryService query() {
        return queryOperations;
    }

    /**
     * 返回删除相关操作入口。
     *
     * @return 删除操作门面
     */
    public OssDeleteService delete() {
        return deleteOperations;
    }

    /**
     * 返回流式解压相关操作入口。
     *
     * @return 解压操作门面
     */
    public OssUnzipService unzip() {
        return streamUnzipOperations;
    }

    /**
     * 返回预签名 URL 相关操作入口。
     *
     * @return 预签名操作门面
     */
    public OssPresignService presign() {
        return presignOperations;
    }

    /**
     * 返回对象/桶标签相关操作入口。
     *
     * @return 标签操作门面
     */
    public OssTaggingService tagging() {
        return taggingOperations;
    }

    /**
     * 返回 Bucket 级管理操作入口。
     *
     * @return Bucket 操作门面
     */
    public OssBucketService bucket() {
        return bucketOperations;
    }

    /**
     * 返回默认 Bucket 名称。
     *
     * <p>Web 适配层只需要读取默认 Bucket 标识，
     * 没必要感知完整内部选项对象，
     * 因此门面只暴露最小读取面。</p>
     *
     * @return 默认 Bucket 名称
     */
    public String getDefaultBucketName() {
        return ossProperties.getBucketName();
    }

    // ----------------------------------------------------------------
    // 私有：构建客户端
    // ----------------------------------------------------------------

    static S3AsyncClient buildClient(OssClientOptions ossProperties) {
        validateClientOptions(ossProperties);
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(ossProperties.getAccessKey(), ossProperties.getSecretKey()));
        long partSizeInBytes = (long) ossProperties.getPartSizeInMb() * 1024 * 1024;
        long multipartThresholdInBytes = (long) ossProperties.getMultipartThresholdInMb() * 1024 * 1024;

        return S3AsyncClient.builder()
                .credentialsProvider(credentials)
                .endpointOverride(URI.create(ossProperties.getEndpoint()))
                .region(Region.US_EAST_1)
                .forcePathStyle(shouldForcePathStyle(ossProperties))
                .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                        .connectionTimeout(Duration.ofMillis(ossProperties.getConnectionTimeout()))
                        .maxConcurrency(ossProperties.getMaxConnections()))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofMillis(ossProperties.getApiCallTimeout()))
                        .apiCallAttemptTimeout(Duration.ofMillis(ossProperties.getApiCallAttemptTimeout()))
                        .build())
                .multipartEnabled(true)
                .multipartConfiguration(MultipartConfiguration.builder()
                        .thresholdInBytes(multipartThresholdInBytes)
                        .minimumPartSizeInBytes(partSizeInBytes)
                        .build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    private static void validateClientOptions(OssClientOptions ossProperties) {
        if (ossProperties.getConnectionTimeout() <= 0) {
            throw new IllegalArgumentException("connectionTimeout 必须大于 0");
        }
        if (ossProperties.getMaxConnections() <= 0) {
            throw new IllegalArgumentException("maxConnections 必须大于 0");
        }
        if (ossProperties.getApiCallAttemptTimeout() <= 0
                || ossProperties.getApiCallTimeout() <= 0
                || ossProperties.getApiCallAttemptTimeout() > ossProperties.getApiCallTimeout()) {
            throw new IllegalArgumentException("API attempt timeout 必须大于 0 且不能超过 API call timeout");
        }
        if (ossProperties.getMultipartThresholdInMb() < 5 || ossProperties.getPartSizeInMb() < 5) {
            throw new IllegalArgumentException("multipart threshold 和 part size 不能小于 5MB");
        }
        if (ossProperties.getPartSizeInMb() > 5120) {
            throw new IllegalArgumentException("part size 不能大于 5120MB");
        }
    }

    private void ensureBucketExists(S3AsyncClient s3Client) {
        String bucketName = ossProperties.getBucketName();
        if (Util.isBlank(bucketName)) {
            return;
        }

        HeadBucketRequest headRequest = HeadBucketRequest.builder().bucket(bucketName).build();
        try {
            CompletableFuture<HeadBucketResponse> future = s3Client.headBucket(headRequest);
            future.join();
        } catch (Exception exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            if (isBucketMissing(cause)) {
                if (ossProperties.isAutoCreateBucket()) {
                    log.info("Bucket [{}] not found, creating automatically...", bucketName);
                    s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build()).join();
                } else {
                    throw OssException.bucketNotFound(bucketName);
                }
            } else {
                throw new OssException("BUCKET_CHECK_FAILED",
                        "Failed to check bucket: " + bucketName, cause);
            }
        }
    }

    /**
     * MinIO 与本地 S3 兼容服务通常要求 Path-Style，否则 SDK 会把 bucket 拼到主机名里。
     */
    private static boolean shouldForcePathStyle(OssClientOptions ossProperties) {
        return "minio".equalsIgnoreCase(ossProperties.getType());
    }

    /**
     * MinIO 在 headBucket 场景下不一定返回 NoSuchBucketException，可能只给通用 404。
     */
    private boolean isBucketMissing(Throwable cause) {
        if (cause instanceof NoSuchBucketException) {
            return true;
        }
        if (cause instanceof S3Exception) {
            S3Exception s3Exception = (S3Exception) cause;
            String errorCode = s3Exception.awsErrorDetails() == null
                    ? null
                    : s3Exception.awsErrorDetails().errorCode();
            return s3Exception.statusCode() == 404
                    || "NoSuchBucket".equalsIgnoreCase(errorCode)
                    || "NotFound".equalsIgnoreCase(errorCode);
        }
        return false;
    }
}


