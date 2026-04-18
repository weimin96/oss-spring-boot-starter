package com.wiblog.oss.controller;

import com.wiblog.oss.contract.AbstractJakartaOssControllerContractTest;
import com.wiblog.oss.service.OssTemplate;

/**
 * Boot4 控制器契约测试入口。
 */
class OssController4UnitTest extends AbstractJakartaOssControllerContractTest<OssController4> {

    @Override
    protected OssController4 createController(OssTemplate ossTemplate) {
        return new OssController4(ossTemplate);
    }
}
