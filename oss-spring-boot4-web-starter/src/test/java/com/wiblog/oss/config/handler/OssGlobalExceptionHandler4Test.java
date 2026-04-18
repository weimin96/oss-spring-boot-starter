package com.wiblog.oss.config.handler;

import com.wiblog.oss.contract.AbstractJakartaOssGlobalExceptionHandlerContractTest;

/**
 * Boot4 异常处理器契约测试入口。
 */
class OssGlobalExceptionHandler4Test
        extends AbstractJakartaOssGlobalExceptionHandlerContractTest<OssGlobalExceptionHandler4> {

    @Override
    protected OssGlobalExceptionHandler4 createHandler() {
        return new OssGlobalExceptionHandler4();
    }
}
