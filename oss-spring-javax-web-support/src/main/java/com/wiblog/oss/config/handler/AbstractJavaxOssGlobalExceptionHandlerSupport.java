package com.wiblog.oss.config.handler;

import com.wiblog.oss.exception.OssException;
import com.wiblog.oss.resp.OssResponse;
import com.wiblog.oss.resp.OssWebExceptionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.validation.ConstraintViolationException;

/**
 * `javax.validation` 体系共享的 OSS HTTP 异常处理实现。
 *
 * <p>版本 Starter 只负责通过 `@RestControllerAdvice` 暴露该行为，
 * 具体错误映射逻辑统一沉到共享支持模块，避免 Boot2 再维护一份独立实现。</p>
 *
 * @author panwm
 */
@Slf4j
public abstract class AbstractJavaxOssGlobalExceptionHandlerSupport {

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
        return OssWebExceptionMapper.businessFailure(ex);
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
        log.warn("OSS validation error: {}", ex.getMessage());
        if (ex instanceof BindException) {
            return OssWebExceptionMapper.validationFailure(formatBindException((BindException) ex));
        }
        return OssWebExceptionMapper.validationFailure(ex);
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
        return OssWebExceptionMapper.unexpectedFailure();
    }

    private String formatBindException(BindException exception) {
        return exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .findFirst()
                .orElse(exception.getMessage());
    }
}
