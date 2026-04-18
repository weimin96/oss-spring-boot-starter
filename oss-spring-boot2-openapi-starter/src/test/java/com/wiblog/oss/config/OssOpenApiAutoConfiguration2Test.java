package com.wiblog.oss.config;

import com.wiblog.oss.config.handler.OssGlobalExceptionHandler2;
import com.wiblog.oss.contract.AbstractJavaxOpenApiAutoConfigurationContractTest;
import com.wiblog.oss.controller.OpenApiOssController2;

/**
 * Boot2 OpenAPI 自动配置契约测试入口。
 */
class OssOpenApiAutoConfiguration2Test
        extends AbstractJavaxOpenApiAutoConfigurationContractTest<OssOpenApiAutoConfiguration2> {

    @Override
    protected OssOpenApiAutoConfiguration2 createOpenApiAutoConfiguration() {
        return new OssOpenApiAutoConfiguration2();
    }

    @Override
    protected Class<?> openApiAutoConfigurationClass() {
        return OssOpenApiAutoConfiguration2.class;
    }

    @Override
    protected Class<?> webAutoConfigurationClass() {
        return OssWebAutoConfiguration2.class;
    }

    @Override
    protected Class<?> controllerClass() {
        return OpenApiOssController2.class;
    }

    @Override
    protected Class<?> handlerClass() {
        return OssGlobalExceptionHandler2.class;
    }
}
