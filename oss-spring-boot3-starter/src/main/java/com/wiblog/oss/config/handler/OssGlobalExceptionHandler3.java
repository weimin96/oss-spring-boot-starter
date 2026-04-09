package com.wiblog.oss.config.handler;

import com.wiblog.oss.exception.OssException;
import com.wiblog.oss.resp.R;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Spring Boot 3.x OSS HTTP 端点全局异常处理器（基于 jakarta.validation）。
 *
 * @author panwm
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.wiblog.oss.controller")
public class OssGlobalExceptionHandler3 {

    @ExceptionHandler(OssException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleOssException(OssException ex) {
        log.warn("OSS business error [{}]: {}", ex.getCode(), ex.getMessage());
        return R.fail(ex.getCode() + ": " + ex.getMessage());
    }

    @ExceptionHandler({BindException.class, ConstraintViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidationException(Exception ex) {
        String msg = ex instanceof BindException be
                ? be.getBindingResult().getFieldErrors().stream()
                        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                        .findFirst().orElse(ex.getMessage())
                : ex.getMessage();
        log.warn("OSS validation error: {}", msg);
        return R.fail(400, msg);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleUnexpected(Exception ex) {
        log.error("OSS unexpected error", ex);
        return R.fail(500, "服务器内部错误，请稍后重试");
    }
}
