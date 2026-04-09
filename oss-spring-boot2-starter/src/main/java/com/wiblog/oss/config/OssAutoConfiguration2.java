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

    @Bean(destroyMethod = "stop", name = "ossTemplate")
    @ConditionalOnMissingBean(OssTemplate.class)
    public OssTemplate ossTemplate(OssProperties2 properties) {
        log.info("Initializing OSS template (Boot2), endpoint={}", properties.getEndpoint());
        return new OssTemplate(properties);
    }

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean(OssController2.class)
    @ConditionalOnProperty(prefix = "oss", name = "http.enable", havingValue = "true")
    public OssController2 ossController(OssTemplate template) {
        log.info("Initializing OSS HTTP endpoint (Boot2)");
        return new OssController2(template);
    }

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean(OssGlobalExceptionHandler2.class)
    @ConditionalOnProperty(prefix = "oss", name = "http.enable", havingValue = "true")
    public OssGlobalExceptionHandler2 ossGlobalExceptionHandler() {
        return new OssGlobalExceptionHandler2();
    }
}
