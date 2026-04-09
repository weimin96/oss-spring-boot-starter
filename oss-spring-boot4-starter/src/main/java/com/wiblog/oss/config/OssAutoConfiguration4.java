package com.wiblog.oss.config;

import com.wiblog.oss.config.handler.OssGlobalExceptionHandler4;
import com.wiblog.oss.controller.OssController4;
import com.wiblog.oss.service.OssTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot 4.x OSS 自动配置。
 *
 * <p>通过 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 注册，适配 Spring Boot 4.x 的新式 SPI 机制。
 *
 * @author panwm
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "oss", name = "enable", havingValue = "true")
@EnableConfigurationProperties(OssProperties4.class)
public class OssAutoConfiguration4 {

    private static final Logger log = LoggerFactory.getLogger(OssAutoConfiguration4.class);

    @Bean(destroyMethod = "stop", name = "ossTemplate")
    @ConditionalOnMissingBean(OssTemplate.class)
    public OssTemplate ossTemplate(OssProperties4 properties) {
        log.info("Initializing OSS template (Boot3), endpoint={}", properties.getEndpoint());
        return new OssTemplate(properties);
    }

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean(OssController4.class)
    @ConditionalOnProperty(prefix = "oss", name = "http.enable", havingValue = "true")
    public OssController4 ossController(OssTemplate template) {
        log.info("Initializing OSS HTTP endpoint (Boot3)");
        return new OssController4(template);
    }

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean(OssGlobalExceptionHandler4.class)
    @ConditionalOnProperty(prefix = "oss", name = "http.enable", havingValue = "true")
    public OssGlobalExceptionHandler4 ossGlobalExceptionHandler() {
        return new OssGlobalExceptionHandler4();
    }
}
