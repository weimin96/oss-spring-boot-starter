package com.wiblog.oss.resp;

import com.wiblog.oss.exception.OssException;

/**
 * Web 层统一异常映射器。
 *
 * <p>各版本 Spring Boot 的异常处理器只负责捕获框架异常，
 * 具体如何转换为统一响应由此类集中定义，
 * 避免 Boot 2/3/4 重复维护同一套返回语义。</p>
 *
 * @author panwm
 */
public final class OssWebExceptionMapper {

    private static final String INTERNAL_ERROR_MESSAGE = "服务器内部错误，请稍后重试";

    private OssWebExceptionMapper() {
    }

    /**
     * 领域异常映射为业务失败响应。
     *
     * @param exception 领域异常
     * @return 统一失败响应
     */
    public static OssResponse<Void> businessFailure(OssException exception) {
        return OssResponse.fail(exception.getCode() + ": " + exception.getMessage());
    }

    /**
     * 参数校验异常映射为 400 响应。
     *
     * @param exception 参数异常
     * @return 统一失败响应
     */
    public static OssResponse<Void> validationFailure(Exception exception) {
        return validationFailure(exception == null ? null : exception.getMessage());
    }

    /**
     * 参数校验消息映射为 400 响应。
     *
     * @param message 参数校验失败消息
     * @return 统一失败响应
     */
    public static OssResponse<Void> validationFailure(String message) {
        if (message == null || message.trim().isEmpty()) {
            return OssResponse.fail(400, "参数校验失败");
        }
        return OssResponse.fail(400, message);
    }

    /**
     * 未知异常映射为 500 响应。
     *
     * @return 统一失败响应
     */
    public static OssResponse<Void> unexpectedFailure() {
        return OssResponse.fail(500, INTERNAL_ERROR_MESSAGE);
    }
}
