package com.wiblog.oss.config.handler;

import com.wiblog.oss.exception.OssException;
import com.wiblog.oss.resp.OssResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.constraints.NotBlank;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OssGlobalExceptionHandler2 测试。
 *
 * <p>异常处理器是对外错误协议的边界，必须验证 Boot2 在业务异常、参数异常和未知异常下的返回语义，
 * 避免前端或调用方收到不稳定的错误结构。</p>
 */
@DisplayName("OssGlobalExceptionHandler2 异常处理器")
class OssGlobalExceptionHandler2Test {

    private final OssGlobalExceptionHandler2 handler = new OssGlobalExceptionHandler2();

    @Test
    @DisplayName("OssException 应被包装为业务失败响应")
    void handleOssException() {
        OssResponse<Void> response = handler.handleOssException(new OssException("OSS_CODE", "业务失败"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMsg()).contains("OSS_CODE").contains("业务失败");
    }

    @Test
    @DisplayName("BindException 应返回首个字段错误")
    void handleBindException() {
        BindException bindException = new BindException(new Object(), "request");
        bindException.addError(new FieldError("request", "path", "不能为空"));

        OssResponse<Void> response = handler.handleValidationException(bindException);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMsg()).isEqualTo("path: 不能为空");
    }

    @Test
    @DisplayName("ConstraintViolationException 应返回原始校验消息")
    void handleConstraintViolationException() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<ValidationTarget>> violations =
                validator.validate(new ValidationTarget(""));

        OssResponse<Void> response = handler.handleValidationException(new ConstraintViolationException(violations));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMsg()).contains("名称不能为空");
    }

    @Test
    @DisplayName("未知异常应返回统一 500 响应")
    void handleUnexpectedException() {
        OssResponse<Void> response = handler.handleUnexpected(new IllegalStateException("boom"));

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
