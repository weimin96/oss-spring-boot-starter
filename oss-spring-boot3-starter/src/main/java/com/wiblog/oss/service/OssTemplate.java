package com.wiblog.oss.service;

import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.exception.OssException;
import com.wiblog.oss.util.Util;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

/**
 * OSS 操作入口。
 *
 * @author panwm
 */
@Slf4j
public class OssTemplate {

    private final OssProperties ossProperties;

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

    public OssTemplate(OssProperties ossProperties) {
        this.ossProperties = ossProperties;
        this.start();
    }

    // ----------------------------------------------------------------
    // 生命周期
    // ----------------------------------------------------------------

    public synchronized void start() {
        this.client = buildClient();
        this.transferManager = S3TransferManager.builder().s3Client(this.client).build();
        ensureBucketExists();
        this.putOperations = new PutOperations(ossProperties, client, transferManager);
        this.queryOperations = new QueryOperations(ossProperties, client, transferManager);
        this.deleteOperations = new DeleteOperations(ossProperties, client, transferManager);
        this.streamUnzipOperations = new StreamUnzipOperations(ossProperties, client, transferManager);
        this.presignOperations = new PresignOperations(ossProperties, client, transferManager);
        this.taggingOperations = new TaggingOperations(ossProperties, client, transferManager);
        this.bucketOperations = new BucketOperations(ossProperties, client, transferManager);
        log.info("OSS initialized - endpoint={}, bucket={}, type={}",
                ossProperties.getEndpoint(), ossProperties.getBucketName(), ossProperties.getType());
    }

    public synchronized void stop() {
        if (this.presignOperations != null) {
            this.presignOperations.close();
            this.presignOperations = null;
        }
        if (this.transferManager != null) {
            this.transferManager.close();
            this.transferManager = null;
        }
        if (this.client != null) {
            this.client.close();
            this.client = null;
        }
        log.info("OSS client closed");
    }

    // ----------------------------------------------------------------
    // 门面方法
    // ----------------------------------------------------------------

    public PutOperations put() {
        return putOperations;
    }

    public QueryOperations query() {
        return queryOperations;
    }

    public DeleteOperations delete() {
        return deleteOperations;
    }

    public StreamUnzipOperations unzip() {
        return streamUnzipOperations;
    }

    public PresignOperations presign() {
        return presignOperations;
    }

    public TaggingOperations tagging() {
        return taggingOperations;
    }

    public BucketOperations bucket() {
        return bucketOperations;
    }

    // ----------------------------------------------------------------
    // 私有：构建客户端
    // ----------------------------------------------------------------

    private S3AsyncClient buildClient() {
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(ossProperties.getAccessKey(), ossProperties.getSecretKey()));

        return S3AsyncClient.crtBuilder()
                .credentialsProvider(credentials)
                .endpointOverride(URI.create(ossProperties.getEndpoint()))
                .region(Region.US_EAST_1)
                // MinIO 需要显式使用 Path-Style，避免被解析为 bucket.localhost 一类的地址。
                .forcePathStyle(shouldForcePathStyle())
                .targetThroughputInGbps(ossProperties.getThroughputInGbps())
                .minimumPartSizeInBytes((long) ossProperties.getPartSizeInMb() * 1024 * 1024)
                .checksumValidationEnabled(false)
                .build();
    }

    private void ensureBucketExists() {
        String bucketName = ossProperties.getBucketName();
        if (Util.isBlank(bucketName)) {
            return;
        }

        HeadBucketRequest headRequest = HeadBucketRequest.builder().bucket(bucketName).build();
        try {
            CompletableFuture<HeadBucketResponse> future = this.client.headBucket(headRequest);
            future.join();
        } catch (Exception exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            if (isBucketMissing(cause)) {
                if (ossProperties.isAutoCreateBucket()) {
                    log.info("Bucket [{}] not found, creating automatically...", bucketName);
                    this.client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build()).join();
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
    private boolean shouldForcePathStyle() {
        return "minio".equalsIgnoreCase(ossProperties.getType());
    }

    /**
     * MinIO 在 headBucket 场景下不一定返回 NoSuchBucketException，可能只给通用 404。
     */
    private boolean isBucketMissing(Throwable cause) {
        if (cause instanceof NoSuchBucketException) {
            return true;
        }
        if (cause instanceof S3Exception s3Exception) {
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