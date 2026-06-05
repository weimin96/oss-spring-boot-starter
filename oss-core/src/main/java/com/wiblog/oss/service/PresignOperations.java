package com.wiblog.oss.service;

import com.wiblog.oss.config.OssClientOptions;
import com.wiblog.oss.util.Util;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.Map;

/**
 * 预签名 URL 操作。
 *
 * <p>预签名 URL 允许无需 AWS 凭证的客户端临时访问或上传对象。
 * 典型场景：前端直传（Pre-signed PUT）、CDN 临时下载链接（Pre-signed GET）。</p>
 *
 * @author panwm
 */
@Slf4j
public class PresignOperations extends Operations implements OssPresignService {

    /**
     * 默认预签名有效期（1 小时）。
     */
    private static final Duration DEFAULT_EXPIRATION = Duration.ofHours(1);

    private final S3Presigner presigner;

    /**
     * 创建预签名操作门面。
     *
     * @param ossProperties   OSS 配置
     * @param client          S3 异步客户端
     * @param transferManager 传输管理器
     */
    public PresignOperations(OssClientOptions ossProperties, S3AsyncClient client,
                             S3TransferManager transferManager) {
        super(ossProperties, client, transferManager);
        this.presigner = buildPresigner(ossProperties);
    }

    // ----------------------------------------------------------------
    // Pre-signed GET（下载）
    // ----------------------------------------------------------------

    /**
     * 生成用于下载的预签名 URL，有效期为默认 1 小时。
     *
     * @param objectName 对象 key
     * @return 预签名 URL 字符串
     */
    @Override
    public String generateGetPresignedUrl(String objectName) {
        return generateGetPresignedUrl(ossProperties.getBucketName(), objectName, DEFAULT_EXPIRATION);
    }

    /**
     * 生成用于下载的预签名 URL，自定义有效期。
     *
     * @param objectName 对象 key
     * @param expiration 有效时长（最大 7 天）
     * @return 预签名 URL 字符串
     */
    @Override
    public String generateGetPresignedUrl(String objectName, Duration expiration) {
        return generateGetPresignedUrl(ossProperties.getBucketName(), objectName, expiration);
    }

    /**
     * 跨 Bucket 生成下载预签名 URL。
     */
    @Override
    public String generateGetPresignedUrl(String bucketName, String objectName, Duration expiration) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(Util.normalizeObjectKey(objectName))
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .getObjectRequest(getRequest)
                .build();

        URL url = presigner.presignGetObject(presignRequest).url();
        log.debug("Generated GET presigned URL for [{}], expires in {}", objectName, expiration);
        return url.toString();
    }

    // ----------------------------------------------------------------
    // Pre-signed PUT（前端直传）
    // ----------------------------------------------------------------

    /**
     * 生成用于上传的预签名 URL，有效期为默认 1 小时。
     *
     * <p>前端可直接用此 URL 发起 HTTP PUT 请求上传文件，无需经过后端服务器。</p>
     *
     * @param objectName  目标对象 key
     * @param contentType 文件 MIME 类型，例如 "image/jpeg"
     * @return 预签名 URL 字符串
     */
    @Override
    public String generatePutPresignedUrl(String objectName, String contentType) {
        return generatePutPresignedUrl(ossProperties.getBucketName(), objectName,
                contentType, DEFAULT_EXPIRATION, null);
    }

    /**
     * 生成用于上传的预签名 URL，支持自定义元数据。
     *
     * @param objectName  目标对象 key
     * @param contentType MIME 类型
     * @param expiration  有效时长
     * @param metadata    自定义元数据（将作为 x-amz-meta-* 头部）
     * @return 预签名 URL 字符串
     */
    @Override
    public String generatePutPresignedUrl(String objectName, String contentType,
                                          Duration expiration, Map<String, String> metadata) {
        return generatePutPresignedUrl(ossProperties.getBucketName(), objectName,
                contentType, expiration, metadata);
    }

    /**
     * 跨 Bucket 生成上传预签名 URL。
     */
    @Override
    public String generatePutPresignedUrl(String bucketName, String objectName,
                                          String contentType, Duration expiration,
                                          Map<String, String> metadata) {
        PutObjectRequest.Builder putBuilder = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(Util.normalizeObjectKey(objectName))
                .contentType(contentType);

        if (metadata != null && !metadata.isEmpty()) {
            putBuilder.metadata(metadata);
        }

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .putObjectRequest(putBuilder.build())
                .build();

        URL url = presigner.presignPutObject(presignRequest).url();
        log.debug("Generated PUT presigned URL for [{}], expires in {}", objectName, expiration);
        return url.toString();
    }

    // ----------------------------------------------------------------
    // 资源释放
    // ----------------------------------------------------------------

    /**
     * 关闭预签名客户端（随 OssTemplate 生命周期管理）。
     */
    public void close() {
        if (presigner != null) {
            presigner.close();
        }
    }

    // ----------------------------------------------------------------
    // 私有工具
    // ----------------------------------------------------------------

    private static S3Presigner buildPresigner(OssClientOptions props) {
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey()));

        S3Presigner.Builder builder = S3Presigner.builder()
                .credentialsProvider(credentials)
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(props.getEndpoint()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(shouldForcePathStyle(props))
                        .build());

        return builder.build();
    }

    private static boolean shouldForcePathStyle(OssClientOptions props) {
        return "minio".equalsIgnoreCase(props.getType());
    }
}


