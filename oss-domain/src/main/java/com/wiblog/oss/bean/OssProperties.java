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

    /**
     * 总开关留在根配置层，是为了让自动装配和纯 Java 接入都能共享同一启用语义。
     */
    private boolean enable = false;

    /**
     * 服务端点单独配置，便于兼容不同厂商或私有部署的对象存储地址。
     */
    private String endpoint;

    /**
     * 默认 Bucket 名称用于简化单 Bucket 场景，避免每次调用都重复传参。
     */
    private String bucketName;

    /**
     * 是否自动创建默认 Bucket 需要显式声明，避免在生产环境隐式修改远端资源状态。
     */
    private boolean autoCreateBucket = false;

    /**
     * 访问密钥 ID 独立保留，便于和 secretKey 分别做配置绑定与审计。
     */
    private String accessKey;

    /**
     * 访问密钥单独建模，是为了保持与各厂商 SDK 认证参数的一致语义。
     */
    private String secretKey;

    /**
     * 对象存储类型参与域名策略和客户端寻址选择，因此必须作为显式配置项保留。
     */
    private String type;

    /**
     * 最大连接数开放给调用方调优，是为了兼顾轻量应用和高并发上传场景。
     */
    private int maxConnections = 50;

    /**
     * 连接超时时间使用毫秒值，便于直接映射到底层客户端配置。
     */
    private long connectionTimeout = 10_000;

    /**
     * 预估吞吐量影响传输管理器并发策略，因此保留为可调参数而不是硬编码常量。
     */
    private double throughputInGbps = 20.0;

    /**
     * 分片大小保持 MB 级配置，方便调用方在大文件吞吐和小文件开销之间做权衡。
     */
    private int partSizeInMb = 10;

    /**
     * HTTP 子配置独立封装，是为了把对象存储核心能力和内置端点开关隔离开。
     */
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
        /**
         * 前缀允许宿主系统把内置接口挂到自定义上下文路径下，避免与现有路由冲突。
         */
        private String prefix = "";

        /**
         * HTTP 开关单独控制，是为了允许只启用 Java API 而不暴露内置 REST 接口。
         */
        private boolean enable = false;
    }
}


