package com.wiblog.oss.config;

import com.wiblog.oss.bean.OssObjectEvent;
import com.wiblog.oss.service.MinioObjectEventListenerContainer;
import com.wiblog.oss.service.OssObjectEventListener;
import com.wiblog.oss.service.OssTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OssAutoConfiguration3 测试。
 *
 * <p>自动装配行为与 starter 的依赖聚合行为分离后，
 * 这里只验证 Boot 3 配置绑定与 Bean 注册是否正确，
 * 避免聚合模块再次承载实现层测试。</p>
 */
@DisplayName("OssAutoConfiguration3 自动配置")
class OssAutoConfiguration3Test {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OssAutoConfiguration3.class));

    @Test
    @DisplayName("工厂方法应只创建 OssTemplate")
    void factoryMethodCreatesTemplate() {
        OssAutoConfiguration3 configuration = new OssAutoConfiguration3();
        OssProperties3 properties = new OssProperties3();
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("ak");
        properties.setSecretKey("sk");
        properties.setBucketName("");
        properties.setType("minio");
        properties.setAutoCreateBucket(false);

        OssTemplate template = configuration.ossTemplate(properties);
        try {
            assertThat(template).isNotNull();
        } finally {
            template.stop();
        }
    }

    @Test
    @DisplayName("启用 OSS 时应注册 OssTemplate")
    void registersTemplateWhenOssEnabled() {
        contextRunner
                .withPropertyValues(
                        "oss.enable=true",
                        "oss.endpoint=http://localhost:9000",
                        "oss.access-key=ak",
                        "oss.secret-key=sk",
                        "oss.type=minio",
                        "oss.bucket-name=",
                        "oss.auto-create-bucket=false")
                .run(context -> assertThat(context).hasSingleBean(OssTemplate.class));
    }

    @Test
    @DisplayName("未启用 OSS 时不应注册 OssTemplate")
    void doesNotRegisterTemplateWhenOssDisabled() {
        contextRunner
                .withPropertyValues("oss.enable=false")
                .run(context -> assertThat(context).doesNotHaveBean(OssTemplate.class));
    }

    @Test
    @DisplayName("未开启事件监听时不应注册监听容器")
    void doesNotRegisterEventContainerWhenEventDisabled() {
        contextRunner
                .withUserConfiguration(EventListenerConfiguration.class)
                .withPropertyValues(
                        "oss.enable=true",
                        "oss.endpoint=http://localhost:9000",
                        "oss.access-key=ak",
                        "oss.secret-key=sk",
                        "oss.type=minio",
                        "oss.bucket-name=",
                        "oss.auto-create-bucket=false",
                        "oss.event.enable=false")
                .run(context -> assertThat(context).doesNotHaveBean(MinioObjectEventListenerContainer.class));
    }

    @Test
    @DisplayName("缺少业务监听器时不应注册监听容器")
    void doesNotRegisterEventContainerWithoutListener() {
        contextRunner
                .withPropertyValues(
                        "oss.enable=true",
                        "oss.endpoint=http://localhost:9000",
                        "oss.access-key=ak",
                        "oss.secret-key=sk",
                        "oss.type=minio",
                        "oss.bucket-name=",
                        "oss.auto-create-bucket=false",
                        "oss.event.enable=true")
                .run(context -> assertThat(context).doesNotHaveBean(MinioObjectEventListenerContainer.class));
    }

    @Test
    @DisplayName("开启事件且存在业务监听器时应注册监听容器")
    void registersEventContainerWhenListenerExists() {
        contextRunner
                .withUserConfiguration(EventListenerConfiguration.class)
                .withPropertyValues(
                        "oss.enable=true",
                        "oss.endpoint=http://localhost:9000",
                        "oss.access-key=ak",
                        "oss.secret-key=sk",
                        "oss.type=minio",
                        "oss.bucket-name=",
                        "oss.auto-create-bucket=false",
                        "oss.event.bucket-name=events",
                        "oss.event.enable=true")
                .run(context -> assertThat(context).hasSingleBean(MinioObjectEventListenerContainer.class));
    }

    @Test
    @DisplayName("非 MinIO 类型不应注册监听容器")
    void doesNotRegisterEventContainerWhenTypeIsNotMinio() {
        contextRunner
                .withUserConfiguration(EventListenerConfiguration.class)
                .withPropertyValues(
                        "oss.enable=true",
                        "oss.endpoint=http://localhost:9000",
                        "oss.access-key=ak",
                        "oss.secret-key=sk",
                        "oss.type=obs",
                        "oss.bucket-name=",
                        "oss.auto-create-bucket=false",
                        "oss.event.enable=true")
                .run(context -> assertThat(context).doesNotHaveBean(MinioObjectEventListenerContainer.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class EventListenerConfiguration {
        @Bean
        OssObjectEventListener ossObjectEventListener() {
            return new OssObjectEventListener() {
                @Override
                public void onObjectChanged(OssObjectEvent event) {
                }
            };
        }
    }
}
