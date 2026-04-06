package com.wiblog.oss.bean;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * OSS 配置属性。
 *
 * @author panwm
 */
@Data
@Validated
@ConfigurationProperties(prefix = OssProperties.PREFIX)
@Schema(description = "OSS 客户端配置")
public class OssProperties {

    public static final String PREFIX = "oss";

    /**
     * 使用布尔值承载启停状态，避免在自动配置阶段引入额外状态对象。
     */
    @Schema(description = "是否启用 OSS 能力")
    private boolean enable = false;

    /**
     * 端点是客户端构建的必要条件，因此声明为必填。
     */
    @NotBlank(message = "oss.endpoint 不能为空")
    @Schema(description = "对象存储服务端点", requiredMode = Schema.RequiredMode.REQUIRED)
    private String endpoint;

    @Schema(description = "默认 bucket 名称")
    private String bucketName;

    @Schema(description = "bucket 不存在时是否自动创建")
    private boolean autoCreateBucket = false;

    @NotBlank(message = "oss.access-key 不能为空")
    @Schema(description = "访问密钥 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String accessKey;

    @NotBlank(message = "oss.secret-key 不能为空")
    @Schema(description = "访问密钥", requiredMode = Schema.RequiredMode.REQUIRED)
    private String secretKey;

    @Schema(description = "对象存储类型，例如 obs、cos、minio")
    private String type;

    /**
     * 明确保留连接配置字段，是为了让配置模型与客户端构建参数保持一致。
     */
    @Min(value = 1, message = "oss.max-connections 最小为 1")
    @Schema(description = "最大连接数")
    private int maxConnections = 50;

    @Min(value = 0, message = "oss.connection-timeout 不能为负数")
    @Schema(description = "连接超时时间，单位为毫秒")
    private long connectionTimeout = 10_000;

    @Schema(description = "目标吞吐量，单位为 Gbps")
    private double throughputInGbps = 20.0;

    @Min(value = 5, message = "oss.part-size-in-mb 最小为 5MB（S3 协议限制）")
    @Schema(description = "分片上传最小分片大小，单位为 MB")
    private int partSizeInMb = 10;

    @Schema(description = "HTTP 端点配置")
    private Http http = new Http();

    public OssProperties() {
    }

    public OssProperties(String endpoint, String accessKey, String secretKey, String type) {
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.type = type;
    }

    public OssProperties(String endpoint, String accessKey, String secretKey, String type, String bucketName) {
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
