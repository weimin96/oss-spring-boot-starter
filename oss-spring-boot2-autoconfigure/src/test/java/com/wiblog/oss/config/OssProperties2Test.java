package com.wiblog.oss.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OssProperties2 校验测试。
 *
 * <p>属性校验属于配置绑定实现的一部分，
 * 因此测试必须跟随配置类一起留在 autoconfigure 模块，
 * 这样才能避免 starter 仅因测试需要重新引入校验 API。</p>
 */
@DisplayName("OssProperties2 配置属性")
class OssProperties2Test {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("合法配置不应产生校验错误")
    void validPropertiesProduceNoViolations() {
        OssProperties2 properties = new OssProperties2();
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("ak");
        properties.setSecretKey("sk");
        properties.setBucketName("test-bucket");
        properties.setType("minio");
        properties.setMaxConnections(10);
        properties.setConnectionTimeout(1000L);
        properties.setPartSizeInMb(5);

        Set<ConstraintViolation<OssProperties2>> violations = validator.validate(properties);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("空白凭证与非法数值应触发校验")
    void invalidPropertiesProduceViolations() {
        OssProperties2 properties = new OssProperties2();
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
        OssProperties2 properties = new OssProperties2();

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
        OssProperties2 properties = new OssProperties2();
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
