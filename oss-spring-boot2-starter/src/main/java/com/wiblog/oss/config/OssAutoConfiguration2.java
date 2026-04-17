package com.wiblog.oss.config;

import com.wiblog.oss.config.handler.OssGlobalExceptionHandler2;
import com.wiblog.oss.controller.OssController2;
import com.wiblog.oss.service.OssTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 2.x OSS 自动配置。
 *
 * <p>通过 {@code spring.factories} 注册，适配 Spring Boot 2.x 的 SPI 机制。
 *
 * @author panwm
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "oss", name = "enable", havingValue = "true")
@EnableConfigurationProperties(OssProperties2.class)
public class OssAutoConfiguration2 {

    private static final Logger log = LoggerFactory.getLogger(OssAutoConfiguration2.class);

    /**
     * 创建 OSS 门面 Bean。
     *
     * <p>自动配置把 `OssTemplate` 作为唯一入口暴露给宿主应用，
     * 让上传、查询、删除等能力共享同一套客户端生命周期。</p>
     *
     * @param properties 绑定后的 OSS 配置
     * @return OSS 门面对象
     */
    @Bean(destroyMethod = "stop", name = "ossTemplate")
    @ConditionalOnMissingBean(OssTemplate.class)
    public OssTemplate ossTemplate(OssProperties2 properties) {
        log.info("Initializing OSS template (Boot2), endpoint={}", properties.getEndpoint());
        return new OssTemplate(properties);
    }

    /**
     * 在 Web 环境下注册 HTTP 控制器。
     *
     * @param template 已初始化的 OSS 门面
     * @return HTTP 控制器
     */
    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean(OssController2.class)
    @ConditionalOnProperty(prefix = "oss", name = "http.enable", havingValue = "true")
    public OssController2 ossController(OssTemplate template) {
        log.info("Initializing OSS HTTP endpoint (Boot2)");
        return new OssController2(template);
    }

    /**
     * 注册统一异常处理器。
     *
     * @return 异常处理器
     */
    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean(OssGlobalExceptionHandler2.class)
    @ConditionalOnProperty(prefix = "oss", name = "http.enable", havingValue = "true")
    public OssGlobalExceptionHandler2 ossGlobalExceptionHandler() {
        return new OssGlobalExceptionHandler2();
    }
}
