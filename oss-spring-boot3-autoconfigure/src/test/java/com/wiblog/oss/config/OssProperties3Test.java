package com.wiblog.oss.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        OssProperties3 properties = new OssProperties3();

        assertThat(properties.getMaxConnections()).isEqualTo(50);
        assertThat(properties.getConnectionTimeout()).isEqualTo(10_000L);
        assertThat(properties.getPartSizeInMb()).isEqualTo(10);
        assertThat(properties.getHttp()).isNotNull();
    }
}
