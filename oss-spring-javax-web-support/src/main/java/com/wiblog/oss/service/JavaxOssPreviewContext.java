package com.wiblog.oss.service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Spring Boot 2.x / javax.servlet 实现的 {@link OssPreviewContext}。
 *
 * @author panwm
 */
public class JavaxOssPreviewContext implements OssPreviewContext {

    private final HttpServletRequest request;
    private final HttpServletResponse response;

    /**
     * 创建基于 `javax.servlet` 的预览上下文。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    public JavaxOssPreviewContext(HttpServletRequest request, HttpServletResponse response) {
        this.request = request;
        this.response = response;
    }

    /**
     * 返回当前 HTTP 方法。
     *
     * @return HTTP 方法
     */
    @Override
    public String getMethod() {
        return request.getMethod();
    }

    /**
     * 返回 Range 请求头。
     *
     * @return Range 值
     */
    @Override
    public String getRangeHeader() {
        return request.getHeader("Range");
    }

    /**
     * 设置响应内容类型。
     *
     * @param contentType 内容类型
     */
    @Override
    public void setContentType(String contentType) {
        response.setContentType(contentType);
    }

    /**
     * 设置响应头。
     *
     * @param name  响应头名称
     * @param value 响应头值
     */
    @Override
    public void setHeader(String name, String value) {
        response.setHeader(name, value);
    }

    /**
     * 设置响应状态码。
     *
     * @param statusCode HTTP 状态码
     */
    @Override
    public void setStatus(int statusCode) {
        response.setStatus(statusCode);
    }

    /**
     * 设置响应体长度。
     *
     * @param length 内容长度
     */
    @Override
    public void setContentLengthLong(long length) {
        response.setContentLengthLong(length);
    }

    /**
     * 获取响应输出流。
     *
     * @return 响应输出流
     * @throws IOException 获取失败时抛出
     */
    @Override
    public OutputStream getOutputStream() throws IOException {
        return response.getOutputStream();
    }

    /**
     * 输出统一的 404 页面。
     *
     * @throws IOException 写响应失败时抛出
     */
    @Override
    public void sendNotFound() throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setHeader("content-type", "text/html;charset=utf-8");
        response.getWriter().println(
                "<html><head><title>404 Not Found</title></head>"
                        + "<body><h1>404 Not Found</h1></body></html>");
    }
}
