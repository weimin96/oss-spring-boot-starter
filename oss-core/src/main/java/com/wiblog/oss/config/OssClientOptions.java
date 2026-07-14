package com.wiblog.oss.config;

import lombok.Data;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * OSS 客户端内部选项。
 *
 * <p>该对象只服务于核心实现和自动装配之间的边界，
 * 目的是把 Spring Boot 的外部配置绑定结果收敛为一个与框架无关的内部选项对象，
 * 避免领域层继续承担配置绑定职责。</p>
 *
 * @author panwm
 */
@Data
public class OssClientOptions {

    private boolean enable = false;
    private String endpoint;
    private String bucketName;
    private boolean autoCreateBucket = false;
    private String accessKey;
    private String secretKey;
    private String type;
    private int maxConnections = 50;
    private long connectionTimeout = 10_000;
    private long apiCallTimeout = 600_000;
    private long apiCallAttemptTimeout = 120_000;
    private int multipartThresholdInMb = 10;
    private int partSizeInMb = 10;
    private Http http = new Http();
    private Event event = new Event();

    /**
     * 创建一个空内部选项对象。
     */
    public OssClientOptions() {
    }

    /**
     * 创建一个不绑定默认 Bucket 的内部选项对象。
     *
     * @param endpoint  对象存储服务端点
     * @param accessKey 访问密钥 ID
     * @param secretKey 访问密钥
     * @param type      对象存储类型
     */
    public OssClientOptions(String endpoint, String accessKey, String secretKey, String type) {
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.type = type;
    }

    /**
     * 创建一个带默认 Bucket 的内部选项对象。
     *
     * @param endpoint   对象存储服务端点
     * @param accessKey  访问密钥 ID
     * @param secretKey  访问密钥
     * @param type       对象存储类型
     * @param bucketName 默认 Bucket 名称
     */
    public OssClientOptions(String endpoint, String accessKey, String secretKey,
                            String type, String bucketName) {
        this(endpoint, accessKey, secretKey, type);
        this.bucketName = bucketName;
    }

    @Data
    public static class Http {
        private String prefix = "";
        private boolean enable = false;
    }

    @Data
    public static class Event {
        private boolean enable = false;
        private String bucketName;
        private List<String> events = Arrays.asList("s3:ObjectCreated:*", "s3:ObjectRemoved:*");
        private String prefix = "";
        private String suffix = "";
        private Duration reconnectInterval = Duration.ofSeconds(5);
    }
}
