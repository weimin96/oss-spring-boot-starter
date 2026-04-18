package com.wiblog.oss.config.handler;

import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Spring Boot 4.x OSS HTTP 全局异常处理器公开类型。
 *
 * <p>Boot4 复用共享 `jakarta` 错误协议实现，
 * 这里只负责以稳定类型暴露 Advice。</p>
 *
 * @author panwm
 */
@RestControllerAdvice(basePackages = "com.wiblog.oss.controller")
public class OssGlobalExceptionHandler4 extends AbstractJakartaOssGlobalExceptionHandlerSupport {
}
