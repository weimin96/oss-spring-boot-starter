package com.wiblog.oss.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 预览上下文契约测试。
 *
 * <p>预览上下文直接连接查询链路与 Servlet API，
 * 这里固定住最关键的请求读取、响应写入和 404 输出语义，
 * 避免不同 Spring Boot 版本在命名空间迁移时出现行为漂移。</p>
 *
 * @param <T> 预览上下文类型
 * @author panwm
 */
public abstract class AbstractOssPreviewContextContractTest<T> {

    /**
     * 创建具体版本的预览上下文。
     *
     * @param request  请求
     * @param response 响应
     * @return 预览上下文
     */
    protected abstract T createPreviewContext(MockHttpServletRequest request, MockHttpServletResponse response);

    @Test
    @DisplayName("普通委派方法应透传到 Servlet 请求与响应")
    void delegatesRequestAndResponseOperations() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/preview/demo.txt");
        request.addHeader("Range", "bytes=0-10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        T context = createPreviewContext(request, response);
        invokeVoid(context, "setContentType", new Class<?>[]{String.class}, "text/plain");
        invokeVoid(context, "setHeader", new Class<?>[]{String.class, String.class}, "Content-Disposition", "inline");
        invokeVoid(context, "setStatus", new Class<?>[]{int.class}, 206);
        invokeVoid(context, "setContentLengthLong", new Class<?>[]{long.class}, 11L);

        Object outputStream = invoke(context, "getOutputStream", new Class<?>[0]);
        invokeOutputStreamWrite(outputStream, "hello world".getBytes(StandardCharsets.UTF_8));

        assertThat(invoke(context, "getMethod", new Class<?>[0])).isEqualTo("GET");
        assertThat(invoke(context, "getRangeHeader", new Class<?>[0])).isEqualTo("bytes=0-10");
        assertThat(response.getContentType()).isEqualTo("text/plain");
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("inline");
        assertThat(response.getStatus()).isEqualTo(206);
        assertThat(response.getContentLengthLong()).isEqualTo(11L);
        assertThat(response.getContentAsString()).isEqualTo("hello world");
    }

    @Test
    @DisplayName("sendNotFound 应输出统一 404 HTML")
    void sendNotFoundWritesHtmlPage() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/preview/missing.txt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        T context = createPreviewContext(request, response);

        invokeVoid(context, "sendNotFound", new Class<?>[0]);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getHeader("content-type")).isEqualTo("text/html;charset=utf-8");
        assertThat(response.getContentAsString()).contains("404 Not Found");
    }

    private void invokeOutputStreamWrite(Object outputStream, byte[] bytes) throws Exception {
        Method writeMethod = outputStream.getClass().getMethod("write", byte[].class);
        writeMethod.invoke(outputStream, new Object[]{bytes});
    }

    private void invokeVoid(Object target, String methodName, Class<?>[] parameterTypes, Object... arguments)
            throws Exception {
        invoke(target, methodName, parameterTypes, arguments);
    }

    private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... arguments)
            throws Exception {
        try {
            return target.getClass().getMethod(methodName, parameterTypes).invoke(target, arguments);
        } catch (InvocationTargetException ex) {
            Throwable targetException = ex.getTargetException();
            if (targetException instanceof Exception) {
                throw (Exception) targetException;
            }
            if (targetException instanceof Error) {
                throw (Error) targetException;
            }
            throw ex;
        }
    }
}
