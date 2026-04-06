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
 * OSS HTTP 端点全局异常处理器
 *
 * @author panwm
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.wiblog.oss.controller")
public class OssGlobalExceptionHandler {

    /**
     * 业务异常（OssException）
     * 统一返回 400，携带 code 字段供调用方区分。
     */
    @ExceptionHandler(OssException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleOssException(OssException ex) {
        log.warn("OSS business error [{}]: {}", ex.getCode(), ex.getMessage());
        return R.fail(ex.getCode() + ": " + ex.getMessage());
    }

    /**
     * 参数绑定/校验异常（@Validated 触发）
     */
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

    /**
     * 兜底：未预期的系统异常
     * 不暴露内部堆栈信息。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleUnexpected(Exception ex) {
        log.error("OSS unexpected error", ex);
        return R.fail(500, "服务器内部错误，请稍后重试");
    }
}
