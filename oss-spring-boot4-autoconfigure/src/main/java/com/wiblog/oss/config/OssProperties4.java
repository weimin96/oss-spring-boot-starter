package com.wiblog.oss.config;

import com.wiblog.oss.config.OssClientOptions.Event;
import com.wiblog.oss.config.OssClientOptions.Http;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Spring Boot 4.x OSS 配置属性（jakarta.validation）。
 *
 * <p>该类型只承担外部配置绑定职责，
 * 之后会在自动装配层显式映射为内部选项对象，
 * 避免核心实现继续依赖 Spring Boot 绑定模型。</p>
 *
 * @author panwm
 */
@Data
@Validated
@ConfigurationProperties(prefix = OssProperties4.PREFIX)
public class OssProperties4 {

    public static final String PREFIX = "oss";

    private boolean enable = false;

    @NotBlank(message = "oss.endpoint 不能为空")
    private String endpoint;

    private String bucketName;
    private boolean autoCreateBucket = false;

    @NotBlank(message = "oss.access-key 不能为空")
    private String accessKey;

    @NotBlank(message = "oss.secret-key 不能为空")
    private String secretKey;

    private String type;

    @Min(value = 1, message = "oss.max-connections 最小为 1")
    private int maxConnections = 50;

    @Min(value = 0, message = "oss.connection-timeout 不能为负数")
    private long connectionTimeout = 10_000;

    private double throughputInGbps = 20.0;

    @Min(value = 5, message = "oss.part-size-in-mb 最小为 5MB")
    private int partSizeInMb = 10;

    @Valid
    private HttpProperties http = new HttpProperties();

    @Valid
    private EventProperties event = new EventProperties();

    /**
     * 转换为核心层内部选项。
     *
     * @return 内部选项对象
     */
    public OssClientOptions toOptions() {
        OssClientOptions options = new OssClientOptions();
        options.setEnable(enable);
        options.setEndpoint(endpoint);
        options.setBucketName(bucketName);
        options.setAutoCreateBucket(autoCreateBucket);
        options.setAccessKey(accessKey);
        options.setSecretKey(secretKey);
        options.setType(type);
        options.setMaxConnections(maxConnections);
        options.setConnectionTimeout(connectionTimeout);
        options.setThroughputInGbps(throughputInGbps);
        options.setPartSizeInMb(partSizeInMb);

        Http httpOptions = new Http();
        httpOptions.setPrefix(http.getPrefix());
        httpOptions.setEnable(http.isEnable());
        options.setHttp(httpOptions);

        Event eventOptions = new Event();
        eventOptions.setEnable(event.isEnable());
        eventOptions.setBucketName(event.getBucketName());
        eventOptions.setEvents(event.getEvents());
        eventOptions.setPrefix(event.getPrefix());
        eventOptions.setSuffix(event.getSuffix());
        eventOptions.setReconnectInterval(event.getReconnectInterval());
        options.setEvent(eventOptions);
        return options;
    }

    /**
     * HTTP 适配配置。
     */
    @Data
    public static class HttpProperties {
        private String prefix = "";
        private boolean enable = false;
    }

    /**
     * MinIO 事件监听配置。
     */
    @Data
    public static class EventProperties {
        private boolean enable = false;
        private String bucketName;
        private List<String> events = Arrays.asList("s3:ObjectCreated:*", "s3:ObjectRemoved:*");
        private String prefix = "";
        private String suffix = "";
        private Duration reconnectInterval = Duration.ofSeconds(5);
    }
}
