package com.wiblog.oss.config;

import com.wiblog.oss.config.handler.OssGlobalExceptionHandler3;
import com.wiblog.oss.contract.AbstractJakartaOpenApiAutoConfigurationContractTest;
import com.wiblog.oss.controller.OpenApiOssController3;

/**
 * Boot3 OpenAPI 自动配置契约测试入口。
 */
class OssOpenApiAutoConfiguration3Test
        extends AbstractJakartaOpenApiAutoConfigurationContractTest<OssOpenApiAutoConfiguration3> {

    @Override
    protected OssOpenApiAutoConfiguration3 createOpenApiAutoConfiguration() {
        return new OssOpenApiAutoConfiguration3();
    }

    @Override
    protected Class<?> openApiAutoConfigurationClass() {
        return OssOpenApiAutoConfiguration3.class;
    }

    @Override
    protected Class<?> webAutoConfigurationClass() {
        return OssWebAutoConfiguration3.class;
    }

    @Override
    protected Class<?> controllerClass() {
        return OpenApiOssController3.class;
    }

    @Override
    protected Class<?> handlerClass() {
        return OssGlobalExceptionHandler3.class;
    }
}
