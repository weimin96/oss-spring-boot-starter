package com.wiblog.oss.config;

import com.wiblog.oss.config.handler.OssGlobalExceptionHandler4;
import com.wiblog.oss.controller.OpenApiOssController4;
import com.wiblog.oss.controller.OssHttpEndpoint;
import com.wiblog.oss.service.OssTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * OssOpenApiAutoConfiguration4 测试。
 */
@DisplayName("OssOpenApiAutoConfiguration4 自动配置")
class OssOpenApiAutoConfiguration4Test {

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OssOpenApiAutoConfiguration4.class, OssWebAutoConfiguration4.class));

    @Test
    @DisplayName("工厂方法应创建带文档元数据的控制器")
    void factoryMethodCreatesDocumentedController() {
        OssOpenApiAutoConfiguration4 configuration = new OssOpenApiAutoConfiguration4();
        OssTemplate template = mock(OssTemplate.class);

        assertThat(configuration.ossController(template)).isInstanceOf(OpenApiOssController4.class);
    }

    @Test
    @DisplayName("文档控制器应暴露 Swagger 元数据")
    void documentedControllerCarriesOpenApiAnnotations() throws NoSuchMethodException {
        Tag tag = OpenApiOssController4.class.getAnnotation(Tag.class);
        Method listObjectsMethod = OpenApiOssController4.class.getMethod("listObjects", String.class);
        Operation operation = listObjectsMethod.getAnnotation(Operation.class);

        assertThat(tag).isNotNull();
        assertThat(tag.name()).isEqualTo("OSS 对象存储接口");
        assertThat(operation).isNotNull();
        assertThat(operation.summary()).isEqualTo("列举指定路径下所有对象");
    }

    @Test
    @DisplayName("文档控制器应兼容 Boot4 方法校验并继承父类参数约束")
    void documentedControllerSupportsMethodValidationWithoutParallelConstraintConflict() throws NoSuchMethodException {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        OpenApiOssController4 controller = new OpenApiOssController4(mock(OssTemplate.class));
        Method listObjectsMethod = OpenApiOssController4.class.getMethod("listObjects", String.class);

        Set<ConstraintViolation<OpenApiOssController4>> blankViolations = validator.forExecutables()
                .validateParameters(controller, listObjectsMethod, new Object[]{" "});
        Set<ConstraintViolation<OpenApiOssController4>> validViolations = validator.forExecutables()
                .validateParameters(controller, listObjectsMethod, new Object[]{"demo"});

        assertThat(blankViolations).hasSize(1);
        assertThat(validViolations).isEmpty();
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
                    assertThat(context).hasSingleBean(OpenApiOssController4.class);
                    assertThat(context).hasSingleBean(OssHttpEndpoint.class);
                    assertThat(context.getBean("ossController")).isExactlyInstanceOf(OpenApiOssController4.class);
                    assertThat(context).hasSingleBean(OssGlobalExceptionHandler4.class);
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
                    assertThat(context).doesNotHaveBean(OpenApiOssController4.class);
                    assertThat(context).doesNotHaveBean(OssGlobalExceptionHandler4.class);
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
                    assertThat(context).doesNotHaveBean(OpenApiOssController4.class);
                    assertThat(context).hasSingleBean(OssGlobalExceptionHandler4.class);
                });
    }
}
