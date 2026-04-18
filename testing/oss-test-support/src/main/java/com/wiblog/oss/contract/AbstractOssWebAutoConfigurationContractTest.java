package com.wiblog.oss.contract;

import com.wiblog.oss.service.OssTemplate;
import com.wiblog.oss.support.OssTestProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * OSS Web 自动配置契约测试。
 *
 * <p>各版本 Web 自动配置的职责一致，区别只在公开类型和注解命名空间。
 * 这组契约测试把 Bean 注册、回退条件和工厂方法行为固定下来，
 * 避免版本模块重复维护相同断言。</p>
 *
 * @param <T> 自动配置类型
 * @author panwm
 */
public abstract class AbstractOssWebAutoConfigurationContractTest<T> {

    /**
     * 创建具体版本的自动配置实例。
     *
     * @return 自动配置实例
     */
    protected abstract T createAutoConfiguration();

    /**
     * @return 自动配置类型
     */
    protected abstract Class<?> autoConfigurationClass();

    /**
     * @return 控制器公开类型
     */
    protected abstract Class<?> controllerClass();

    /**
     * @return 异常处理器公开类型
     */
    protected abstract Class<?> handlerClass();

    @Test
    @DisplayName("工厂方法应创建控制器与异常处理器")
    void factoryMethodsCreateBeans() throws Exception {
        T configuration = createAutoConfiguration();
        OssTemplate template = mock(OssTemplate.class);

        Method controllerFactoryMethod = configuration.getClass().getMethod("ossController", OssTemplate.class);
        Method handlerFactoryMethod = configuration.getClass().getMethod("ossGlobalExceptionHandler");

        assertThat(controllerFactoryMethod.invoke(configuration, template)).isInstanceOf(controllerClass());
        assertThat(handlerFactoryMethod.invoke(configuration)).isInstanceOf(handlerClass());
    }

    @Test
    @DisplayName("启用 HTTP 端点时应注册控制器和异常处理器")
    void registersControllerAndHandlerWhenHttpEnabled() {
        newContextRunner()
                .withBean(OssTemplate.class, () -> mock(OssTemplate.class))
                .withPropertyValues(OssTestProperties.httpProperties(true))
                .run(context -> {
                    assertThat(context).hasSingleBean(controllerClass());
                    assertThat(context).hasSingleBean(handlerClass());
                });
    }

    @Test
    @DisplayName("未启用 HTTP 端点时不应注册控制器和异常处理器")
    void doesNotRegisterControllerWhenHttpDisabled() {
        newContextRunner()
                .withBean(OssTemplate.class, () -> mock(OssTemplate.class))
                .withPropertyValues(OssTestProperties.httpProperties(false))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(controllerClass());
                    assertThat(context).doesNotHaveBean(handlerClass());
                });
    }

    @Test
    @DisplayName("已有自定义 HTTP 端点时应让出控制器注册")
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
                .withConfiguration(AutoConfigurations.of(autoConfigurationClass()));
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
