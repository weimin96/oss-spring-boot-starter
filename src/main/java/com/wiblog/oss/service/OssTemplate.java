package com.wiblog.oss.service;

import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.util.Util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

/**
 * @author panwm
 * @since 2023/8/20 1:33
 */
@Slf4j
public class OssTemplate {

    private final OssProperties ossProperties;

    /**
     * Amazon S3 异步客户端
     */
    private S3AsyncClient client;

    /**
     * 用于管理 S3 数据传输的高级工具
     */
    private S3TransferManager transferManager;

    private PutOperations putOperations;

    private QueryOperations queryOperations;

    private DeleteOperations deleteOperations;

    public OssTemplate(OssProperties ossProperties) {
        Assert.notNull(ossProperties.getEndpoint(), "illegal argument oss.endpoint");
        Assert.notNull(ossProperties.getAccessKey(), "illegal argument oss.access-key");
        Assert.notNull(ossProperties.getSecretKey(), "illegal argument oss.secret-key");
        this.ossProperties = ossProperties;
        this.start();
    }

    public void start() {
        // 凭证
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(ossProperties.getAccessKey(), ossProperties.getSecretKey()));

        this.client = S3AsyncClient.crtBuilder()
                .credentialsProvider(credentialsProvider)
                .endpointOverride(URI.create(ossProperties.getEndpoint()))
                .region(Region.US_EAST_1)
                .targetThroughputInGbps(20.0)
                .minimumPartSizeInBytes(10 * 1024 * 1024L)
                .checksumValidationEnabled(false)
                .build();

        //AWS基于 CRT 的 S3 AsyncClient 实例用作 S3 传输管理器的底层客户端
        this.transferManager = S3TransferManager.builder().s3Client(this.client).build();

        // 创建存储桶
        createBucket();
        // 初始化
        initOperations();
    }

    public void stop() {
        this.client.close();
        this.transferManager.close();
    }

    private void createBucket() {
        if (!Util.isBlank(ossProperties.getBucketName())) {
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder().bucket(ossProperties.getBucketName()).build();
            try {
                CompletableFuture<HeadBucketResponse> headBucketResponseCompletableFuture = this.client.headBucket(headBucketRequest);
                headBucketResponseCompletableFuture.join();
            } catch (Exception e) {
                if (e.getCause() instanceof NoSuchBucketException) {
                    // 自动创建
                    if (ossProperties.isAutoCreateBucket()) {
                        CreateBucketRequest bucketRequest = CreateBucketRequest.builder()
                                .bucket(ossProperties.getBucketName())
                                .build();
                        this.client.createBucket(bucketRequest);
                    } else {
                        throw new IllegalArgumentException("bucket not found");
                    }
                } else {
                    log.error(e.getMessage(), e);
                }

            }
        }
    }

    private void initOperations() {
        this.putOperations = new PutOperations(this.ossProperties, this.client, this.transferManager);
        this.queryOperations = new QueryOperations(this.ossProperties, this.client, this.transferManager);
        this.deleteOperations = new DeleteOperations(this.ossProperties, this.client, this.transferManager);
    }

    public PutOperations put() {
        return this.putOperations;
    }

    public QueryOperations query() {
        return this.queryOperations;
    }

    public DeleteOperations delete() {
        return this.deleteOperations;
    }

}
