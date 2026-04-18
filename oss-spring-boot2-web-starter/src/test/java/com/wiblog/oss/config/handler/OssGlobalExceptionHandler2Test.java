package com.wiblog.oss.config.handler;

import com.wiblog.oss.contract.AbstractJavaxOssGlobalExceptionHandlerContractTest;

/**
 * Boot2 异常处理器契约测试入口。
 */
class OssGlobalExceptionHandler2Test
        extends AbstractJavaxOssGlobalExceptionHandlerContractTest<OssGlobalExceptionHandler2> {

    @Override
    protected OssGlobalExceptionHandler2 createHandler() {
        return new OssGlobalExceptionHandler2();
    }
}
