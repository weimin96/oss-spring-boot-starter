package com.wiblog.oss.config;

import com.wiblog.oss.config.handler.OssGlobalExceptionHandler2;
import com.wiblog.oss.contract.AbstractJavaxOssWebAutoConfigurationContractTest;
import com.wiblog.oss.controller.OssController2;

/**
 * Boot2 Web 自动配置契约测试入口。
 */
class OssWebAutoConfiguration2Test extends AbstractJavaxOssWebAutoConfigurationContractTest<OssWebAutoConfiguration2> {

    @Override
    protected OssWebAutoConfiguration2 createAutoConfiguration() {
        return new OssWebAutoConfiguration2();
    }

    @Override
    protected Class<?> autoConfigurationClass() {
        return OssWebAutoConfiguration2.class;
    }

    @Override
    protected Class<?> controllerClass() {
        return OssController2.class;
    }

    @Override
    protected Class<?> handlerClass() {
        return OssGlobalExceptionHandler2.class;
    }
}
