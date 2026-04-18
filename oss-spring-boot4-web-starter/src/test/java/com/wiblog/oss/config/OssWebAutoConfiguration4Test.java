package com.wiblog.oss.config;

import com.wiblog.oss.config.handler.OssGlobalExceptionHandler4;
import com.wiblog.oss.contract.AbstractJakartaOssWebAutoConfigurationContractTest;
import com.wiblog.oss.controller.OssController4;

/**
 * Boot4 Web 自动配置契约测试入口。
 */
class OssWebAutoConfiguration4Test extends AbstractJakartaOssWebAutoConfigurationContractTest<OssWebAutoConfiguration4> {

    @Override
    protected OssWebAutoConfiguration4 createAutoConfiguration() {
        return new OssWebAutoConfiguration4();
    }

    @Override
    protected Class<?> autoConfigurationClass() {
        return OssWebAutoConfiguration4.class;
    }

    @Override
    protected Class<?> controllerClass() {
        return OssController4.class;
    }

    @Override
    protected Class<?> handlerClass() {
        return OssGlobalExceptionHandler4.class;
    }
}
