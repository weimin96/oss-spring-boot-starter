package com.wiblog.oss.config.handler;

import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Spring Boot 2.x OSS HTTP 全局异常处理器公开类型。
 *
 * <p>具体错误映射逻辑已经沉到共享 `javax` 支持模块，
 * 这里仅负责把公共行为挂载到 Boot2 的控制器包。</p>
 *
 * @author panwm
 */
@RestControllerAdvice(basePackages = "com.wiblog.oss.controller")
public class OssGlobalExceptionHandler2 extends AbstractJavaxOssGlobalExceptionHandlerSupport {
}
