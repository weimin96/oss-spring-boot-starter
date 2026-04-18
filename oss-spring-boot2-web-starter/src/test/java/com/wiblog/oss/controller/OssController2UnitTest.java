package com.wiblog.oss.controller;

import com.wiblog.oss.contract.AbstractJavaxOssControllerContractTest;
import com.wiblog.oss.service.OssTemplate;

/**
 * Boot2 控制器契约测试入口。
 *
 * <p>具体断言已经收口到 `oss-javax-test-support`，
 * 本地只保留公开类型的构造方式，避免版本模块继续维护大段重复测试。</p>
 */
class OssController2UnitTest extends AbstractJavaxOssControllerContractTest<OssController2> {

    @Override
    protected OssController2 createController(OssTemplate ossTemplate) {
        return new OssController2(ossTemplate);
    }
}
