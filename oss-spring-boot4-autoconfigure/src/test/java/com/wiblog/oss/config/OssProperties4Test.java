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
 * OssProperties4 校验测试。
 *
 * <p>Boot 4 与 Boot 3 一样使用 jakarta.validation，
 * 但仍需单独跟随配置绑定实现验证，防止跨版本 starter 重新承载校验职责。</p>
 */
@DisplayName("OssProperties4 配置属性")
class OssProperties4Test {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("合法配置不应产生校验错误")
    void validPropertiesProduceNoViolations() {
        OssProperties4 properties = new OssProperties4();
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("ak");
        properties.setSecretKey("sk");
        properties.setBucketName("test-bucket");
        properties.setType("minio");
        properties.setMaxConnections(10);
        properties.setConnectionTimeout(1000L);
        properties.setPartSizeInMb(5);

        Set<ConstraintViolation<OssProperties4>> violations = validator.validate(properties);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("空白凭证与非法数值应触发校验")
    void invalidPropertiesProduceViolations() {
        OssProperties4 properties = new OssProperties4();
        properties.setEndpoint(" ");
        properties.setAccessKey("");
        properties.setSecretKey(null);
        properties.setMaxConnections(0);
        properties.setConnectionTimeout(-1L);
        properties.setPartSizeInMb(4);

        Set<String> violationMessages = validator.validate(properties).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());

        assertThat(violationMessages).contains(
                "oss.endpoint 不能为空",
                "oss.access-key 不能为空",
                "oss.secret-key 不能为空",
                "oss.max-connections 最小为 1",
                "oss.connection-timeout 不能为负数",
                "oss.part-size-in-mb 最小为 5MB");
    }

    @Test
    @DisplayName("默认值应保持稳定")
    void defaultsStayStable() {
        OssProperties4 properties = new OssProperties4();

        assertThat(properties.getMaxConnections()).isEqualTo(50);
        assertThat(properties.getConnectionTimeout()).isEqualTo(10000L);
        assertThat(properties.getPartSizeInMb()).isEqualTo(10);
        assertThat(properties.getHttp()).isNotNull();
        assertThat(properties.getEvent()).isNotNull();
        assertThat(properties.getEvent().isEnable()).isFalse();
        assertThat(properties.getEvent().getEvents()).containsExactly("s3:ObjectCreated:*", "s3:ObjectRemoved:*");
        assertThat(properties.getEvent().getReconnectInterval()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("事件配置应映射到核心选项")
    void eventPropertiesMapToOptions() {
        OssProperties4 properties = new OssProperties4();
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
