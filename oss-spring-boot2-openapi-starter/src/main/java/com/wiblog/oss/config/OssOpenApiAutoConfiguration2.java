package com.wiblog.oss.config;

import com.wiblog.oss.controller.OpenApiOssController2;
import com.wiblog.oss.controller.OssHttpEndpoint;
import com.wiblog.oss.service.OssTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 2.x OSS OpenAPI 自动配置。
 *
 * <p>OpenAPI Starter 只在用户明确引入时注册带 Swagger 注解的 HTTP 控制器，
 * 并通过端点标记接口抢占 Web Starter 的默认控制器装配。</p>
 *
 * @author panwm
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore(OssWebAutoConfiguration2.class)
@ConditionalOnProperty(prefix = "oss", name = "enable", havingValue = "true")
public class OssOpenApiAutoConfiguration2 {

    private static final Logger log = LoggerFactory.getLogger(OssOpenApiAutoConfiguration2.class);

    /**
     * 注册带 OpenAPI 元数据的 HTTP 控制器。
     *
     * @param template 已初始化的 OSS 门面
     * @return 文档控制器
     */
    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean(OssHttpEndpoint.class)
    @ConditionalOnProperty(prefix = "oss", name = "http.enable", havingValue = "true")
    public OpenApiOssController2 ossController(OssTemplate template) {
        log.info("Initializing OSS HTTP endpoint with OpenAPI metadata (Boot2)");
        return new OpenApiOssController2(template);
    }
}
