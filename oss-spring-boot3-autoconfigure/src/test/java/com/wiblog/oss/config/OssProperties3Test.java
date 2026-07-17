package com.wiblog.oss.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OssProperties3 校验测试。
 *
 * <p>Boot 3 的 jakarta.validation 约束属于自动装配实现细节，
 * 因此测试需要与配置类一起迁回 autoconfigure 模块，
 * 防止纯聚合 starter 为了测试再次暴露校验依赖。</p>
 */
@DisplayName("OssProperties3 配置属性")
class OssProperties3Test {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("合法配置不应产生校验错误")
    void validPropertiesProduceNoViolations() {
        OssProperties3 properties = new OssProperties3();
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("ak");
        properties.setSecretKey("sk");
        properties.setBucketName("test-bucket");
        properties.setType("minio");
        properties.setMaxConnections(10);
        properties.setConnectionTimeout(1_000L);
        properties.setPartSizeInMb(5);

        Set<ConstraintViolation<OssProperties3>> violations = validator.validate(properties);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("空白凭证与非法数值应触发校验")
    void invalidPropertiesProduceViolations() {
        OssProperties3 properties = new OssProperties3();
        properties.setEndpoint(" ");
        properties.setAccessKey("");
        properties.setSecretKey(null);
        properties.setMaxConnections(0);
        properties.setConnectionTimeout(0L);
        properties.setApiCallTimeout(1_000L);
        properties.setApiCallAttemptTimeout(2_000L);
        properties.setMultipartThresholdInMb(4);
        properties.setPartSizeInMb(4);

        Set<String> violationMessages = validator.validate(properties).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());

        assertThat(violationMessages).contains(
                "oss.endpoint 不能为空",
                "oss.access-key 不能为空",
                "oss.secret-key 不能为空",
                "oss.max-connections 最小为 1",
                "oss.connection-timeout 最小为 1ms",
                "oss.api-call-attempt-timeout 不能大于 oss.api-call-timeout",
                "oss.multipart-threshold-in-mb 最小为 5MB",
                "oss.part-size-in-mb 最小为 5MB");
    }

    @Test
    @DisplayName("分片大小超过 S3 上限应触发校验")
    void partSizeAboveS3LimitProducesViolation() {
        OssProperties3 properties = new OssProperties3();
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("ak");
        properties.setSecretKey("sk");
        properties.setPartSizeInMb(5121);

        Set<String> violationMessages = validator.validate(properties).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());

        assertThat(violationMessages).contains("oss.part-size-in-mb 最大为 5120MB");
    }

    @Test
    @DisplayName("默认值应保持稳定")
    void defaultsStayStable() {
        OssProperties3 properties = new OssProperties3();

        assertThat(properties.getMaxConnections()).isEqualTo(50);
        assertThat(properties.getConnectionTimeout()).isEqualTo(10_000L);
        assertThat(properties.getApiCallTimeout()).isEqualTo(600_000L);
        assertThat(properties.getApiCallAttemptTimeout()).isEqualTo(120_000L);
        assertThat(properties.getMultipartThresholdInMb()).isEqualTo(10);
        assertThat(properties.getPartSizeInMb()).isEqualTo(10);
        assertThat(properties.getHttp()).isNotNull();
        assertThat(properties.getEvent()).isNotNull();
        assertThat(properties.getEvent().isEnable()).isFalse();
        assertThat(properties.getEvent().getEvents()).containsExactly("s3:ObjectCreated:*", "s3:ObjectRemoved:*");
        assertThat(properties.getEvent().getReconnectInterval()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("客户端配置应映射到核心选项")
    void clientPropertiesMapToOptions() {
        OssProperties3 properties = new OssProperties3();
        properties.setConnectionTimeout(2_000L);
        properties.setMaxConnections(80);
        properties.setApiCallTimeout(300_000L);
        properties.setApiCallAttemptTimeout(60_000L);
        properties.setMultipartThresholdInMb(32);
        properties.setPartSizeInMb(16);

        OssClientOptions options = properties.toOptions();

        assertThat(options.getConnectionTimeout()).isEqualTo(2_000L);
        assertThat(options.getMaxConnections()).isEqualTo(80);
        assertThat(options.getApiCallTimeout()).isEqualTo(300_000L);
        assertThat(options.getApiCallAttemptTimeout()).isEqualTo(60_000L);
        assertThat(options.getMultipartThresholdInMb()).isEqualTo(32);
        assertThat(options.getPartSizeInMb()).isEqualTo(16);
    }

    @Test
    @DisplayName("事件配置应映射到核心选项")
    void eventPropertiesMapToOptions() {
        OssProperties3 properties = new OssProperties3();
        properties.getEvent().setEnable(true);
        properties.getEvent().setBucketName("events-bucket");
        properties.getEvent().setEvents(Arrays.asList("s3:ObjectCreated:Put"));
        properties.getEvent().setPrefix("images/");
        properties.getEvent().setSuffix(".jpg");
        properties.getEvent().setReconnectInterval(Duration.ofSeconds(3));

        OssClientOptions options = properties.toOptions();

        assertThat(options.getEvent().isEnable()).isTrue();
        assertThat(options.getEvent().getBucketName()).isEqualTo("events-bucket");
        assertThat(options.getEvent().getEvents()).containsExactly("s3:ObjectCreated:Put");
        assertThat(options.getEvent().getPrefix()).isEqualTo("images/");
        assertThat(options.getEvent().getSuffix()).isEqualTo(".jpg");
        assertThat(options.getEvent().getReconnectInterval()).isEqualTo(Duration.ofSeconds(3));
    }
}
