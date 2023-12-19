package com.wiblog.oss.bean;


import com.amazonaws.ClientConfiguration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author panwm
 */
@Data
@ConfigurationProperties(prefix = OssProperties.PREFIX)
public class OssProperties {

    /**
     * 配置前缀
     */
    public static final String PREFIX = "oss";

    /**
     * 是否启用 oss，默认为：false
     */
    private boolean enable = false;

    /**
     * 端点
     */
    private String endpoint;

    /**
     * bucket名称
     */
    private String bucketName;

    /**
     * 自动创建bucket
     */
    private boolean autoCreateBucket = false;

    /**
     * 访问密钥id
     */
    private String accessKey;

    /**
     * 访问密钥
     */
    private String secretKey;

    /**
     * 是否允许跨域 默认不允许
     */
    private boolean cross = false;

    /**
     * oss类型 obs/minio
     */
    private String type;

    /**
     * 最大连接数 默认50
     */
    private int maxConnections = ClientConfiguration.DEFAULT_MAX_CONNECTIONS;

    /**
     * 连接超时 默认10s
     */
    private int connectionTimeout = ClientConfiguration.DEFAULT_CONNECTION_TIMEOUT;

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
    public static class Http {

        /**
         * 访问端点前缀
         */
        private String prefix = "";

        /**
         * 是否启用Web端点 默认true
         */
        private boolean enable = false;

    }

}
