package com.wiblog.oss.config;

import com.wiblog.oss.config.handler.OssGlobalExceptionHandler3;
import com.wiblog.oss.contract.AbstractJakartaOssWebAutoConfigurationContractTest;
import com.wiblog.oss.controller.OssController3;

/**
 * Boot3 Web 自动配置契约测试入口。
 */
class OssWebAutoConfiguration3Test extends AbstractJakartaOssWebAutoConfigurationContractTest<OssWebAutoConfiguration3> {

    @Override
    protected OssWebAutoConfiguration3 createAutoConfiguration() {
        return new OssWebAutoConfiguration3();
    }

    @Override
    protected Class<?> autoConfigurationClass() {
        return OssWebAutoConfiguration3.class;
    }

    @Override
    protected Class<?> controllerClass() {
        return OssController3.class;
    }

    @Override
    protected Class<?> handlerClass() {
        return OssGlobalExceptionHandler3.class;
    }
}
