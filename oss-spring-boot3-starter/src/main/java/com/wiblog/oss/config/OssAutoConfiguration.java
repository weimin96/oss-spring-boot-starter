package com.wiblog.oss.config;

import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.config.handler.OssGlobalExceptionHandler;
import com.wiblog.oss.controller.OssController;
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
 * OSS 自动配置类
 *
 * @author panwm
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = OssProperties.PREFIX, name = "enable", havingValue = "true")
@EnableConfigurationProperties(OssProperties.class)
public class OssAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OssAutoConfiguration.class);

    /**
     * OSS 操作模板 Bean。
     * 允许用户声明自定义 OssTemplate Bean 来覆盖。
     */
    @Bean(destroyMethod = "stop", name = "ossTemplate")
    @ConditionalOnMissingBean(OssTemplate.class)
    public OssTemplate ossTemplate(OssProperties properties) {
        log.info("Initializing OSS template, endpoint={}", properties.getEndpoint());
        return new OssTemplate(properties);
    }

    /**
     * OSS HTTP 端点（可选）。
     * 仅在 Web 环境且 oss.http.enable=true 时注册。
     */
    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean(OssController.class)
    @ConditionalOnProperty(prefix = OssProperties.PREFIX, name = "http.enable", havingValue = "true")
    public OssController ossController(OssTemplate template) {
        log.info("Initializing OSS HTTP endpoint");
        return new OssController(template);
    }

    /**
     * 全局异常处理器（改进新增）。
     * 仅在 Web 环境且 HTTP 端点启用时注册，保持最小化原则。
     */
    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean(OssGlobalExceptionHandler.class)
    @ConditionalOnProperty(prefix = OssProperties.PREFIX, name = "http.enable", havingValue = "true")
    public OssGlobalExceptionHandler ossGlobalExceptionHandler() {
        return new OssGlobalExceptionHandler();
    }
}
