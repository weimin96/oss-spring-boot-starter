package com.wiblog.oss.service;

import java.io.IOException;
import java.io.OutputStream;

/**
 * HTTP 预览/下载请求上下文抽象。
 *
 * <p>core 模块不引入任何 Servlet API（javax / jakarta），
 * 通过此接口隔离差异：
 * <ul>
 *   <li>Spring Boot 2 starter 使用 {@code javax.servlet} 实现</li>
 *   <li>Spring Boot 3/4 starter 使用 {@code jakarta.servlet} 实现</li>
 * </ul>
 *
 * @author panwm
 */
public interface OssPreviewContext {

    /**
     * 获取 HTTP 请求方法（GET / HEAD 等）
     */
    String getMethod();

    /**
     * 获取 Range 请求头，不存在时返回 null
     */
    String getRangeHeader();

    /**
     * 设置响应 Content-Type
     */
    void setContentType(String contentType);

    /**
     * 设置响应头
     */
    void setHeader(String name, String value);

    /**
     * 设置响应状态码
     */
    void setStatus(int statusCode);

    /**
     * 设置 Content-Length
     */
    void setContentLengthLong(long length);

    /**
     * 获取响应输出流
     */
    OutputStream getOutputStream() throws IOException;

    /**
     * 发送 404 响应（包含简单 HTML body）。
     * 默认实现：状态码 404 + HTML 提示，子类可覆盖。
     */
    default void sendNotFound() throws IOException {
        setStatus(404);
        setHeader("content-type", "text/html;charset=utf-8");
        OutputStream out = getOutputStream();
        out.write("<html><head><title>404 Not Found</title></head>"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.write("<body><h1>404 Not Found</h1></body></html>"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.flush();
    }
}


