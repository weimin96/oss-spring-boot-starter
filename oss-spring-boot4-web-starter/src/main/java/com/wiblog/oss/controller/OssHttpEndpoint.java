package com.wiblog.oss.controller;

/**
 * 标记 OSS HTTP 端点实现。
 *
 * <p>Web Starter 和 OpenAPI Starter 都会注册控制器实现，
 * 自动装配通过该标记接口判断当前是否已经存在可用端点，避免重复注册同一路由。</p>
 */
public interface OssHttpEndpoint {
}
