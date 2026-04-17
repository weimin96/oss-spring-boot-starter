package com.wiblog.oss.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OssProperties2 校验测试。
 *
 * <p>属性类本身没有业务逻辑，测试重点是约束是否按 Boot2 的 javax.validation 体系正确生效，
 * 避免配置错误被延迟到运行期才暴露。</p>
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
    @DisplayName("继承的默认值应保持稳定")
    void inheritedDefaultsStayStable() {
        OssProperties2 properties = new OssProperties2();

        assertThat(properties.getMaxConnections()).isEqualTo(50);
        assertThat(properties.getConnectionTimeout()).isEqualTo(10000L);
        assertThat(properties.getPartSizeInMb()).isEqualTo(10);
        assertThat(properties.getHttp()).isNotNull();
    }
}
