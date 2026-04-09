package com.wiblog.oss.config;

import com.wiblog.oss.config.handler.OssGlobalExceptionHandler3;
import com.wiblog.oss.controller.OssController3;
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
 * Spring Boot 3.x OSS 自动配置。
 *
 * @author panwm
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "oss", name = "enable", havingValue = "true")
@EnableConfigurationProperties(OssProperties3.class)
public class OssAutoConfiguration3 {

    private static final Logger log = LoggerFactory.getLogger(OssAutoConfiguration3.class);

    @Bean(destroyMethod = "stop", name = "ossTemplate")
    @ConditionalOnMissingBean(OssTemplate.class)
    public OssTemplate ossTemplate(OssProperties3 properties) {
        log.info("Initializing OSS template (Boot3), endpoint={}", properties.getEndpoint());
        return new OssTemplate(properties);
    }

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean(OssController3.class)
    @ConditionalOnProperty(prefix = "oss", name = "http.enable", havingValue = "true")
    public OssController3 ossController(OssTemplate template) {
        log.info("Initializing OSS HTTP endpoint (Boot3)");
        return new OssController3(template);
    }

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean(OssGlobalExceptionHandler3.class)
    @ConditionalOnProperty(prefix = "oss", name = "http.enable", havingValue = "true")
    public OssGlobalExceptionHandler3 ossGlobalExceptionHandler() {
        return new OssGlobalExceptionHandler3();
    }
}
