package com.wiblog.oss.contract;

import com.wiblog.oss.service.OssTemplate;
import com.wiblog.oss.support.OssTestProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * `jakarta.validation` 命名空间 OpenAPI 自动配置契约测试。
 *
 * @param <T> 自动配置类型
 * @author panwm
 */
public abstract class AbstractJakartaOpenApiAutoConfigurationContractTest<T> {

    /**
     * @return OpenAPI 自动配置实例
     */
    protected abstract T createOpenApiAutoConfiguration();

    /**
     * @return OpenAPI 自动配置类型
     */
    protected abstract Class<?> openApiAutoConfigurationClass();

    /**
     * @return Web 自动配置类型
     */
    protected abstract Class<?> webAutoConfigurationClass();

    /**
     * @return 文档控制器公开类型
     */
    protected abstract Class<?> controllerClass();

    /**
     * @return 异常处理器公开类型
     */
    protected abstract Class<?> handlerClass();

    @Test
    @DisplayName("工厂方法应创建带文档元数据的控制器")
    void factoryMethodCreatesDocumentedController() throws Exception {
        T configuration = createOpenApiAutoConfiguration();
        Method controllerFactoryMethod = configuration.getClass().getMethod("ossController", OssTemplate.class);

        assertThat(controllerFactoryMethod.invoke(configuration, mock(OssTemplate.class))).isInstanceOf(controllerClass());
    }

    @Test
    @DisplayName("文档控制器应暴露 Swagger 元数据")
    void documentedControllerCarriesOpenApiAnnotations() throws Exception {
        Tag tag = controllerClass().getAnnotation(Tag.class);
        Method listObjectsMethod = controllerClass().getMethod("listObjects", String.class);
        Operation operation = listObjectsMethod.getAnnotation(Operation.class);

        assertThat(tag).isNotNull();
        assertThat(tag.name()).isEqualTo("OSS 对象存储接口");
        assertThat(operation).isNotNull();
        assertThat(operation.summary()).isEqualTo("列举指定路径下所有对象");
    }

    @Test
    @DisplayName("文档控制器应兼容方法校验并继承父类参数约束")
    void documentedControllerSupportsMethodValidation() throws Exception {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Object controller = controllerClass().getConstructor(OssTemplate.class).newInstance(mock(OssTemplate.class));
        Method listObjectsMethod = controllerClass().getMethod("listObjects", String.class);

        Set<?> blankViolations = validator.forExecutables()
                .validateParameters(controller, listObjectsMethod, new Object[]{" "});
        Set<?> validViolations = validator.forExecutables()
                .validateParameters(controller, listObjectsMethod, new Object[]{"demo"});

        assertThat(blankViolations).hasSize(1);
        assertThat(validViolations).isEmpty();
    }

    @Test
    @DisplayName("启用 HTTP 端点时应优先注册带文档元数据的控制器")
    void registersOpenApiControllerWhenHttpEnabled() {
        newContextRunner()
                .withBean(OssTemplate.class, () -> mock(OssTemplate.class))
                .withPropertyValues(OssTestProperties.httpProperties(true))
                .run(context -> {
                    assertThat(context).hasSingleBean(controllerClass());
                    assertThat(context).hasSingleBean((Class) resolveEndpointMarkerType());
                    assertThat(context.getBean("ossController")).isExactlyInstanceOf(controllerClass());
                    assertThat(context).hasSingleBean(handlerClass());
                });
    }

    @Test
    @DisplayName("未启用 HTTP 端点时不应注册文档控制器")
    void doesNotRegisterOpenApiControllerWhenHttpDisabled() {
        newContextRunner()
                .withBean(OssTemplate.class, () -> mock(OssTemplate.class))
                .withPropertyValues(OssTestProperties.httpProperties(false))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(controllerClass());
                    assertThat(context).doesNotHaveBean(handlerClass());
                });
    }

    @Test
    @DisplayName("已有自定义 HTTP 端点时应让出文档控制器注册")
    void backsOffWhenEndpointAlreadyProvided() {
        Class<?> endpointMarkerType = resolveEndpointMarkerType();

        newContextRunner()
                .withBean(OssTemplate.class, () -> mock(OssTemplate.class))
                .withBean((Class) endpointMarkerType, () -> createEndpointMarkerBean(endpointMarkerType))
                .withPropertyValues(OssTestProperties.httpProperties(true))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(controllerClass());
                    assertThat(context).hasSingleBean(handlerClass());
                });
    }

    private WebApplicationContextRunner newContextRunner() {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(openApiAutoConfigurationClass(), webAutoConfigurationClass()));
    }

    private Class<?> resolveEndpointMarkerType() {
        Class<?> currentType = controllerClass();
        while (currentType != null) {
            Class<?>[] interfaces = currentType.getInterfaces();
            for (int i = 0; i < interfaces.length; i++) {
                if ("com.wiblog.oss.controller.OssHttpEndpoint".equals(interfaces[i].getName())) {
                    return interfaces[i];
                }
            }
            currentType = currentType.getSuperclass();
        }
        throw new IllegalStateException("未找到 OssHttpEndpoint 标记接口: " + controllerClass().getName());
    }

    private Object createEndpointMarkerBean(Class<?> endpointMarkerType) {
        return Proxy.newProxyInstance(
                endpointMarkerType.getClassLoader(),
                new Class<?>[]{endpointMarkerType},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("toString".equals(methodName)) {
                        return "test-oss-http-endpoint";
                    }
                    if ("hashCode".equals(methodName)) {
                        return 0;
                    }
                    if ("equals".equals(methodName)) {
                        return proxy == args[0];
                    }
                    return null;
                });
    }
}
