package com.wiblog.oss.config;

import com.wiblog.oss.config.handler.OssGlobalExceptionHandler2;
import com.wiblog.oss.controller.OpenApiOssController2;
import com.wiblog.oss.controller.OssHttpEndpoint;
import com.wiblog.oss.openapi.CommonOssOpenApiOperations;
import com.wiblog.oss.service.OssTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * OssOpenApiAutoConfiguration2 测试。
 */
@DisplayName("OssOpenApiAutoConfiguration2 自动配置")
class OssOpenApiAutoConfiguration2Test {

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OssOpenApiAutoConfiguration2.class, OssWebAutoConfiguration2.class));

    @Test
    @DisplayName("工厂方法应创建带文档元数据的控制器")
    void factoryMethodCreatesDocumentedController() {
        OssOpenApiAutoConfiguration2 configuration = new OssOpenApiAutoConfiguration2();
        OssTemplate template = mock(OssTemplate.class);

        assertThat(configuration.ossController(template)).isInstanceOf(OpenApiOssController2.class);
    }

    @Test
    @DisplayName("文档控制器应暴露 Swagger 元数据")
    void documentedControllerCarriesOpenApiAnnotations() throws NoSuchMethodException {
        Tag tag = CommonOssOpenApiOperations.class.getAnnotation(Tag.class);
        Method uploadObjectMethod = OpenApiOssController2.class.getMethod(
                "uploadObject",
                MultipartFile.class, String.class, String.class);
        Operation operation = uploadObjectMethod.getAnnotation(Operation.class);

        assertThat(CommonOssOpenApiOperations.class.isAssignableFrom(OpenApiOssController2.class)).isTrue();
        assertThat(tag).isNotNull();
        assertThat(tag.name()).isEqualTo("OSS 对象存储接口");
        assertThat(operation).isNotNull();
        assertThat(operation.summary()).isEqualTo("上传文件");
    }

    @Test
    @DisplayName("启用 HTTP 端点时应优先注册带文档元数据的控制器")
    void registersOpenApiControllerWhenHttpEnabled() {
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
                    assertThat(context).hasSingleBean(OpenApiOssController2.class);
                    assertThat(context).hasSingleBean(OssHttpEndpoint.class);
                    assertThat(context.getBean("ossController")).isExactlyInstanceOf(OpenApiOssController2.class);
                    assertThat(context).hasSingleBean(OssGlobalExceptionHandler2.class);
                });
    }

    @Test
    @DisplayName("未启用 HTTP 端点时不应注册文档控制器")
    void doesNotRegisterOpenApiControllerWhenHttpDisabled() {
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
                    assertThat(context).doesNotHaveBean(OpenApiOssController2.class);
                    assertThat(context).doesNotHaveBean(OssGlobalExceptionHandler2.class);
                });
    }

    @Test
    @DisplayName("已有自定义 HTTP 端点时应让出文档控制器注册")
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
                    assertThat(context).doesNotHaveBean(OpenApiOssController2.class);
                    assertThat(context).hasSingleBean(OssGlobalExceptionHandler2.class);
                });
    }
}
