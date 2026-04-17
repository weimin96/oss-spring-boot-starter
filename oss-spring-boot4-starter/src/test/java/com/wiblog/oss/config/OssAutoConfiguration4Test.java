package com.wiblog.oss.config;

import com.wiblog.oss.config.handler.OssGlobalExceptionHandler4;
import com.wiblog.oss.controller.OssController4;
import com.wiblog.oss.service.OssTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * OssAutoConfiguration4 测试。
 *
 * <p>Boot4 已切到新的自动配置 SPI，
 * 这里验证工厂方法与条件注解仍保持和低版本一致的装配语义，避免升级框架后 Starter 行为分叉。</p>
 */
@DisplayName("OssAutoConfiguration4 自动配置")
class OssAutoConfiguration4Test {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OssAutoConfiguration4.class));
    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OssAutoConfiguration4.class));

    @Test
    @DisplayName("工厂方法应能创建控制器与异常处理器")
    void factoryMethodsCreateBeans() {
        OssAutoConfiguration4 configuration = new OssAutoConfiguration4();
        OssProperties4 properties = new OssProperties4();
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("ak");
        properties.setSecretKey("sk");
        properties.setBucketName("");
        properties.setType("minio");
        properties.setAutoCreateBucket(false);

        OssTemplate template = configuration.ossTemplate(properties);
        try {
            assertThat(template).isNotNull();
            assertThat(configuration.ossController(template)).isInstanceOf(OssController4.class);
            assertThat(configuration.ossGlobalExceptionHandler()).isInstanceOf(OssGlobalExceptionHandler4.class);
        } finally {
            template.stop();
        }
    }

    @Test
    @DisplayName("启用 OSS 与 HTTP 端点时应注册控制器和异常处理器")
    void registersControllerAndHandlerWhenHttpEnabled() {
        webContextRunner
                .withBean(OssTemplate.class, () -> mock(OssTemplate.class))
                .withPropertyValues(
                        "oss.enable=true",
                        "oss.http.enable=true",
                        "oss.endpoint=http://localhost:9000",
                        "oss.access-key=ak",
                        "oss.secret-key=sk",
                        "oss.type=minio",
                        "oss.bucket-name=test-bucket")
                .run(context -> {
                    assertThat(context).hasSingleBean(OssController4.class);
                    assertThat(context).hasSingleBean(OssGlobalExceptionHandler4.class);
                });
    }

    @Test
    @DisplayName("未启用 HTTP 端点时不应注册控制器和异常处理器")
    void doesNotRegisterControllerWhenHttpDisabled() {
        webContextRunner
                .withBean(OssTemplate.class, () -> mock(OssTemplate.class))
                .withPropertyValues(
                        "oss.enable=true",
                        "oss.http.enable=false",
                        "oss.endpoint=http://localhost:9000",
                        "oss.access-key=ak",
                        "oss.secret-key=sk",
                        "oss.type=minio",
                        "oss.bucket-name=test-bucket")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OssController4.class);
                    assertThat(context).doesNotHaveBean(OssGlobalExceptionHandler4.class);
                });
    }

    @Test
    @DisplayName("未启用 OSS 时不应装配 Starter Bean")
    void doesNotRegisterBeansWhenOssDisabled() {
        contextRunner
                .withPropertyValues("oss.enable=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OssTemplate.class);
                    assertThat(context).doesNotHaveBean(OssController4.class);
                    assertThat(context).doesNotHaveBean(OssGlobalExceptionHandler4.class);
                });
    }
}
