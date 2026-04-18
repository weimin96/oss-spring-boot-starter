package com.wiblog.oss.config.handler;

import com.wiblog.oss.contract.AbstractJakartaOssGlobalExceptionHandlerContractTest;

/**
 * Boot3 异常处理器契约测试入口。
 */
class OssGlobalExceptionHandler3Test
        extends AbstractJakartaOssGlobalExceptionHandlerContractTest<OssGlobalExceptionHandler3> {

    @Override
    protected OssGlobalExceptionHandler3 createHandler() {
        return new OssGlobalExceptionHandler3();
    }
}
