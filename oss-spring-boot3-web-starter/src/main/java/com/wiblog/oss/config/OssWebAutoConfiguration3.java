package com.wiblog.oss.config;

import com.wiblog.oss.config.handler.OssGlobalExceptionHandler3;
import com.wiblog.oss.controller.OssController3;
import com.wiblog.oss.controller.OssHttpEndpoint;
import com.wiblog.oss.service.OssTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot 3.x OSS Web 自动配置。
 *
 * <p>Web Starter 只注册 HTTP 端点与异常处理器，
 * 并依赖基础 Starter 提供的 {@link OssTemplate}。</p>
 *
 * @author panwm
 */
@AutoConfiguration(after = OssAutoConfiguration3.class)
@ConditionalOnProperty(prefix = "oss", name = "enable", havingValue = "true")
public class OssWebAutoConfiguration3 {

    private static final Logger log = LoggerFactory.getLogger(OssWebAutoConfiguration3.class);

    /**
     * 注册 HTTP 控制器。
     *
     * @param template 已初始化的 OSS 门面
     * @return HTTP 控制器
     */
    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean(OssHttpEndpoint.class)
    @ConditionalOnProperty(prefix = "oss", name = "http.enable", havingValue = "true")
    public OssController3 ossController(OssTemplate template) {
        log.info("Initializing OSS HTTP endpoint (Boot3 Web Starter)");
        return new OssController3(template);
    }

    /**
     * 注册统一异常处理器。
     *
     * @return 异常处理器
     */
    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean(OssGlobalExceptionHandler3.class)
    @ConditionalOnProperty(prefix = "oss", name = "http.enable", havingValue = "true")
    public OssGlobalExceptionHandler3 ossGlobalExceptionHandler() {
        return new OssGlobalExceptionHandler3();
    }
}
