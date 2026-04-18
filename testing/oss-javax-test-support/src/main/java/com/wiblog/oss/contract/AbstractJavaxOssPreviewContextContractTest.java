package com.wiblog.oss.contract;

import com.wiblog.oss.service.JavaxOssPreviewContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * `javax` 命名空间预览上下文契约测试。
 *
 * @author panwm
 */
public abstract class AbstractJavaxOssPreviewContextContractTest
        extends AbstractOssPreviewContextContractTest<JavaxOssPreviewContext> {

    @Override
    protected JavaxOssPreviewContext createPreviewContext(
            MockHttpServletRequest request, MockHttpServletResponse response) {
        return new JavaxOssPreviewContext(request, response);
    }
}
