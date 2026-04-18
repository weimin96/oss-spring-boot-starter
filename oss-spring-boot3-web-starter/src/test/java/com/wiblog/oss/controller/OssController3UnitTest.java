package com.wiblog.oss.controller;

import com.wiblog.oss.contract.AbstractJakartaOssControllerContractTest;
import com.wiblog.oss.service.OssTemplate;

/**
 * Boot3 控制器契约测试入口。
 */
class OssController3UnitTest extends AbstractJakartaOssControllerContractTest<OssController3> {

    @Override
    protected OssController3 createController(OssTemplate ossTemplate) {
        return new OssController3(ossTemplate);
    }
}
