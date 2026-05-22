package com.wiblog.oss.config;

import com.wiblog.oss.service.MinioObjectEventListenerContainer;
import com.wiblog.oss.service.OssObjectEventListener;
import com.wiblog.oss.service.OssTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Spring Boot 2.x OSS 基础自动配置。
 *
 * <p>基础 Starter 只负责创建 {@link OssTemplate} 和绑定配置属性，
 * 不再承载 Web 控制器与文档相关能力，避免普通 Java API 使用者被动引入 Web 依赖。</p>
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
     * @param properties 绑定后的 OSS 配置
     * @return OSS 门面对象
     */
    @Bean(destroyMethod = "stop", name = "ossTemplate")
    @ConditionalOnMissingBean(OssTemplate.class)
    public OssTemplate ossTemplate(OssProperties2 properties) {
        log.info("Initializing OSS template (Boot2), endpoint={}", properties.getEndpoint());
        return new OssTemplate(properties.toOptions());
    }

    /**
     * 创建 MinIO 对象事件监听容器。
     *
     * @param properties 绑定后的 OSS 配置
     * @param listeners  业务事件监听器
     * @return MinIO 对象事件监听容器
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnBean(OssObjectEventListener.class)
    @ConditionalOnMissingBean(MinioObjectEventListenerContainer.class)
    @ConditionalOnProperty(prefix = "oss.event", name = "enable", havingValue = "true")
    public MinioObjectEventListenerContainer minioObjectEventListenerContainer(
            OssProperties2 properties, List<OssObjectEventListener> listeners) {
        return new MinioObjectEventListenerContainer(properties.toOptions(), listeners);
    }
}
