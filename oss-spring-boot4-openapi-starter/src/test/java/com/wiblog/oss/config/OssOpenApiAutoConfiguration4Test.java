package com.wiblog.oss.config;

import com.wiblog.oss.config.handler.OssGlobalExceptionHandler4;
import com.wiblog.oss.contract.AbstractJakartaOpenApiAutoConfigurationContractTest;
import com.wiblog.oss.controller.OpenApiOssController4;

/**
 * Boot4 OpenAPI 自动配置契约测试入口。
 */
class OssOpenApiAutoConfiguration4Test
        extends AbstractJakartaOpenApiAutoConfigurationContractTest<OssOpenApiAutoConfiguration4> {

    @Override
    protected OssOpenApiAutoConfiguration4 createOpenApiAutoConfiguration() {
        return new OssOpenApiAutoConfiguration4();
    }

    @Override
    protected Class<?> openApiAutoConfigurationClass() {
        return OssOpenApiAutoConfiguration4.class;
    }

    @Override
    protected Class<?> webAutoConfigurationClass() {
        return OssWebAutoConfiguration4.class;
    }

    @Override
    protected Class<?> controllerClass() {
        return OpenApiOssController4.class;
    }

    @Override
    protected Class<?> handlerClass() {
        return OssGlobalExceptionHandler4.class;
    }
}
