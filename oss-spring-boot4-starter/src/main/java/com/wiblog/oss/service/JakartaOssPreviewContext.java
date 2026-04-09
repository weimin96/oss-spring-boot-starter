package com.wiblog.oss.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Spring Boot 4.x / jakarta.servlet 实现的 {@link OssPreviewContext}。
 *
 * @author panwm
 */
public class JakartaOssPreviewContext implements OssPreviewContext {

    private final HttpServletRequest request;
    private final HttpServletResponse response;

    public JakartaOssPreviewContext(HttpServletRequest request, HttpServletResponse response) {
        this.request = request;
        this.response = response;
    }

    @Override
    public String getMethod() { return request.getMethod(); }

    @Override
    public String getRangeHeader() { return request.getHeader("Range"); }

    @Override
    public void setContentType(String contentType) { response.setContentType(contentType); }

    @Override
    public void setHeader(String name, String value) { response.setHeader(name, value); }

    @Override
    public void setStatus(int statusCode) { response.setStatus(statusCode); }

    @Override
    public void setContentLengthLong(long length) { response.setContentLengthLong(length); }

    @Override
    public OutputStream getOutputStream() throws IOException { return response.getOutputStream(); }

    @Override
    public void sendNotFound() throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setHeader("content-type", "text/html;charset=utf-8");
        response.getWriter().println(
                "<html><head><title>404 Not Found</title></head>"
                        + "<body><h1>404 Not Found</h1></body></html>");
    }
}
