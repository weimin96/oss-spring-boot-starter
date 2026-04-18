package com.wiblog.oss.contract;

import com.wiblog.oss.config.handler.AbstractJakartaOssGlobalExceptionHandlerSupport;
import com.wiblog.oss.exception.OssException;
import com.wiblog.oss.resp.OssResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `jakarta.validation` 命名空间异常处理契约测试。
 *
 * @param <T> 异常处理器类型
 * @author panwm
 */
public abstract class AbstractJakartaOssGlobalExceptionHandlerContractTest<T extends AbstractJakartaOssGlobalExceptionHandlerSupport> {

    /**
     * @return 具体版本异常处理器
     */
    protected abstract T createHandler();

    @Test
    @DisplayName("OssException 应被包装为业务失败响应")
    void handleOssException() {
        OssResponse<Void> response = createHandler().handleOssException(new OssException("OSS_CODE", "业务失败"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMsg()).contains("OSS_CODE").contains("业务失败");
    }

    @Test
    @DisplayName("BindException 应返回首个字段错误")
    void handleBindException() {
        BindException bindException = new BindException(new Object(), "request");
        bindException.addError(new FieldError("request", "path", "不能为空"));

        OssResponse<Void> response = createHandler().handleValidationException(bindException);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMsg()).isEqualTo("path: 不能为空");
    }

    @Test
    @DisplayName("ConstraintViolationException 应返回原始校验消息")
    void handleConstraintViolationException() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<ValidationTarget>> violations = validator.validate(new ValidationTarget(""));

        OssResponse<Void> response = createHandler()
                .handleValidationException(new ConstraintViolationException(violations));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMsg()).contains("名称不能为空");
    }

    @Test
    @DisplayName("未知异常应返回统一 500 响应")
    void handleUnexpectedException() {
        OssResponse<Void> response = createHandler().handleUnexpected(new IllegalStateException("boom"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMsg()).isEqualTo("服务器内部错误，请稍后重试");
    }

    private static final class ValidationTarget {

        @NotBlank(message = "名称不能为空")
        private final String name;

        private ValidationTarget(String name) {
            this.name = name;
        }
    }
}
