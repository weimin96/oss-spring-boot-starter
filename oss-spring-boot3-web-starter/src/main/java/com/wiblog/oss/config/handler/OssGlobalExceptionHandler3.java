package com.wiblog.oss.config.handler;

import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Spring Boot 3.x OSS HTTP 全局异常处理器公开类型。
 *
 * <p>错误协议逻辑已经收敛到共享 `jakarta` 支持模块，
 * 版本模块只保留 Advice 暴露与稳定类名。</p>
 *
 * @author panwm
 */
@RestControllerAdvice(basePackages = "com.wiblog.oss.controller")
public class OssGlobalExceptionHandler3 extends AbstractJakartaOssGlobalExceptionHandlerSupport {
}
