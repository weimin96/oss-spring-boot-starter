package com.wiblog.oss.config;

import com.wiblog.oss.service.OssTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot 4.x OSS 基础自动配置。
 *
 * <p>基础 Starter 只负责创建 {@link OssTemplate} 和绑定配置属性，
 * 不再承载 Web 控制器与文档相关能力，避免普通 Java API 使用者被动引入 Web 依赖。</p>
 *
 * @author panwm
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "oss", name = "enable", havingValue = "true")
@EnableConfigurationProperties(OssProperties4.class)
public class OssAutoConfiguration4 {

    private static final Logger log = LoggerFactory.getLogger(OssAutoConfiguration4.class);

    /**
     * 创建 OSS 门面 Bean。
     *
     * @param properties 绑定后的 OSS 配置
     * @return OSS 门面对象
     */
    @Bean(destroyMethod = "stop", name = "ossTemplate")
    @ConditionalOnMissingBean(OssTemplate.class)
    public OssTemplate ossTemplate(OssProperties4 properties) {
        log.info("Initializing OSS template (Boot4), endpoint={}", properties.getEndpoint());
        return new OssTemplate(properties.toOptions());
    }
}
