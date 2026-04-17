package com.wiblog.oss.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JavaxOssPreviewContext 测试。
 *
 * <p>预览上下文直接连接 OSS 预览逻辑与 Servlet API，
 * 这里验证委派行为和 404 输出格式，避免不同 Boot 版本下出现响应语义漂移。</p>
 */
@DisplayName("JavaxOssPreviewContext 预览上下文")
class JavaxOssPreviewContextTest {

    @Test
    @DisplayName("普通委派方法应透传到 Servlet 请求与响应")
    void delegatesRequestAndResponseOperations() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/preview/demo.txt");
        request.addHeader("Range", "bytes=0-10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        JavaxOssPreviewContext context = new JavaxOssPreviewContext(request, response);
        context.setContentType("text/plain");
        context.setHeader("Content-Disposition", "inline");
        context.setStatus(206);
        context.setContentLengthLong(11);
        context.getOutputStream().write("hello world".getBytes(StandardCharsets.UTF_8));

        assertThat(context.getMethod()).isEqualTo("GET");
        assertThat(context.getRangeHeader()).isEqualTo("bytes=0-10");
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
        JavaxOssPreviewContext context = new JavaxOssPreviewContext(request, response);

        context.sendNotFound();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getHeader("content-type")).isEqualTo("text/html;charset=utf-8");
        assertThat(response.getContentAsString()).contains("404 Not Found");
    }
}
