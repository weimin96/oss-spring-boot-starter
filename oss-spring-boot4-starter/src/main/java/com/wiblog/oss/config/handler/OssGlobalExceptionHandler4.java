package com.wiblog.oss.config.handler;

import com.wiblog.oss.exception.OssException;
import com.wiblog.oss.resp.OssResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Spring Boot 4.x OSS HTTP 端点全局异常处理器（基于 jakarta.validation）。
 *
 * @author panwm
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.wiblog.oss.controller")
public class OssGlobalExceptionHandler4 {

    /**
     * 把领域异常转换为统一业务失败响应。
     *
     * @param ex 领域异常
     * @return 统一响应
     */
    @ExceptionHandler(OssException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public OssResponse<Void> handleOssException(OssException ex) {
        log.warn("OSS business error [{}]: {}", ex.getCode(), ex.getMessage());
        return OssResponse.fail(ex.getCode() + ": " + ex.getMessage());
    }

    /**
     * 把参数绑定或校验异常转换为 400 响应。
     *
     * @param ex 参数异常
     * @return 统一响应
     */
    @ExceptionHandler({BindException.class, ConstraintViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public OssResponse<Void> handleValidationException(Exception ex) {
        String msg = ex instanceof BindException be
                ? be.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst().orElse(ex.getMessage())
                : ex.getMessage();
        log.warn("OSS validation error: {}", msg);
        return OssResponse.fail(400, msg);
    }

    /**
     * 把未知异常转换为统一 500 响应。
     *
     * @param ex 未知异常
     * @return 统一响应
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public OssResponse<Void> handleUnexpected(Exception ex) {
        log.error("OSS unexpected error", ex);
        return OssResponse.fail(500, "服务器内部错误，请稍后重试");
    }
}
