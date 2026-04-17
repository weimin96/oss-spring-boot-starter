package com.wiblog.oss.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JakartaOssPreviewContext 测试。
 *
 * <p>预览上下文是 HTTP 下载/预览链路的适配边界，
 * 这里验证常规委派和 404 输出，确保 Boot4 迁移到 jakarta.servlet 后行为不变。</p>
 */
@DisplayName("JakartaOssPreviewContext 预览上下文")
class JakartaOssPreviewContextTest {

    @Test
    @DisplayName("普通委派方法应透传到 Servlet 请求与响应")
    void delegatesRequestAndResponseOperations() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/preview/demo.txt");
        request.addHeader("Range", "bytes=0-10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        JakartaOssPreviewContext context = new JakartaOssPreviewContext(request, response);
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
        JakartaOssPreviewContext context = new JakartaOssPreviewContext(request, response);

        context.sendNotFound();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getHeader("content-type")).isEqualTo("text/html;charset=utf-8");
        assertThat(response.getContentAsString()).contains("404 Not Found");
    }
}
