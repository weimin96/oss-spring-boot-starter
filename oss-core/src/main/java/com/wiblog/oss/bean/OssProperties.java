package com.wiblog.oss.bean;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * OSS 配置属性
 *
 * @author panwm
 */
@Data
@Schema(description = "OSS 客户端配置")
public class OssProperties {

    public static final String PREFIX = "oss";

    @Schema(description = "是否启用 OSS 能力")
    private boolean enable = false;

    @Schema(description = "对象存储服务端点", requiredMode = Schema.RequiredMode.REQUIRED)
    private String endpoint;

    @Schema(description = "默认 bucket 名称")
    private String bucketName;

    @Schema(description = "bucket 不存在时是否自动创建")
    private boolean autoCreateBucket = false;

    @Schema(description = "访问密钥 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String accessKey;

    @Schema(description = "访问密钥", requiredMode = Schema.RequiredMode.REQUIRED)
    private String secretKey;

    @Schema(description = "对象存储类型，例如 obs、cos、minio")
    private String type;

    @Schema(description = "最大连接数")
    private int maxConnections = 50;

    @Schema(description = "连接超时时间，单位为毫秒")
    private long connectionTimeout = 10_000;

    @Schema(description = "目标吞吐量，单位为 Gbps")
    private double throughputInGbps = 20.0;

    @Schema(description = "分片上传最小分片大小，单位为 MB")
    private int partSizeInMb = 10;

    @Schema(description = "HTTP 端点配置")
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
    @Schema(description = "OSS HTTP 端点配置")
    public static class Http {

        @Schema(description = "HTTP 端点路径前缀")
        private String prefix = "";

        @Schema(description = "是否启用 HTTP 端点")
        private boolean enable = false;
    }
}
