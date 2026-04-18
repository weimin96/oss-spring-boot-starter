package com.wiblog.oss.config;

import com.wiblog.oss.config.handler.OssGlobalExceptionHandler4;
import com.wiblog.oss.controller.OssController4;
import com.wiblog.oss.controller.OssHttpEndpoint;
import com.wiblog.oss.service.OssTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * OssWebAutoConfiguration4 测试。
 */
@DisplayName("OssWebAutoConfiguration4 自动配置")
class OssWebAutoConfiguration4Test {

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OssWebAutoConfiguration4.class));

    @Test
    @DisplayName("工厂方法应创建控制器与异常处理器")
    void factoryMethodsCreateBeans() {
        OssWebAutoConfiguration4 configuration = new OssWebAutoConfiguration4();
        OssTemplate template = mock(OssTemplate.class);

        assertThat(configuration.ossController(template)).isInstanceOf(OssController4.class);
        assertThat(configuration.ossGlobalExceptionHandler()).isInstanceOf(OssGlobalExceptionHandler4.class);
    }

    @Test
    @DisplayName("启用 HTTP 端点时应注册控制器和异常处理器")
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
    @DisplayName("已有自定义 HTTP 端点时应让出控制器注册")
    void backsOffWhenEndpointAlreadyProvided() {
        webContextRunner
                .withBean(OssTemplate.class, () -> mock(OssTemplate.class))
                .withBean(OssHttpEndpoint.class, () -> new OssHttpEndpoint() { })
                .withPropertyValues(
                        "oss.enable=true",
                        "oss.http.enable=true",
                        "oss.endpoint=http://localhost:9000",
                        "oss.access-key=ak",
                        "oss.secret-key=sk",
                        "oss.type=minio",
                        "oss.bucket-name=test-bucket")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OssController4.class);
                    assertThat(context).hasSingleBean(OssGlobalExceptionHandler4.class);
                });
    }
}
