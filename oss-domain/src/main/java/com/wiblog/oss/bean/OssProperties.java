package com.wiblog.oss.bean;

import lombok.Data;

/**
 * OSS 配置属性
 *
 * @author panwm
 */
@Data
public class OssProperties {

    public static final String PREFIX = "oss";
    private boolean enable = false;
    private String endpoint;
    private String bucketName;
    private boolean autoCreateBucket = false;
    private String accessKey;
    private String secretKey;
    private String type;
    private int maxConnections = 50;
    private long connectionTimeout = 10_000;
    private double throughputInGbps = 20.0;
    private int partSizeInMb = 10;
    private Http http = new Http();

    /**
     * 创建一个空配置对象。
     *
     * <p>保留无参构造的原因是兼容 Spring Boot `@ConfigurationProperties` 绑定，
     * 让宿主应用可以通过配置文件而不是手工 new 的方式组装客户端参数。</p>
     */
    public OssProperties() {
    }

    /**
     * 创建一个不绑定默认 Bucket 的配置对象。
     *
     * @param endpoint  对象存储服务端点
     * @param accessKey 访问密钥 ID
     * @param secretKey 访问密钥
     * @param type      对象存储类型，例如 `obs`、`cos`、`minio`
     */
    public OssProperties(String endpoint, String accessKey, String secretKey, String type) {
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.type = type;
    }

    /**
     * 创建一个带默认 Bucket 的配置对象。
     *
     * <p>该重载主要用于测试和纯 Java 接入场景，
     * 让调用方可以一次性声明连接参数与默认操作 Bucket。</p>
     *
     * @param endpoint   对象存储服务端点
     * @param accessKey  访问密钥 ID
     * @param secretKey  访问密钥
     * @param type       对象存储类型
     * @param bucketName 默认 Bucket 名称
     */
    public OssProperties(String endpoint, String accessKey, String secretKey,
                         String type, String bucketName) {
        this(endpoint, accessKey, secretKey, type);
        this.bucketName = bucketName;
    }

    @Data
    public static class Http {
        private String prefix = "";
        private boolean enable = false;
    }
}


