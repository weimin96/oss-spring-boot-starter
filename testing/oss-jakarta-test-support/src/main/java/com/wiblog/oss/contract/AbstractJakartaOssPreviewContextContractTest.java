package com.wiblog.oss.contract;

import com.wiblog.oss.service.JakartaOssPreviewContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * `jakarta` 命名空间预览上下文契约测试。
 *
 * @author panwm
 */
public abstract class AbstractJakartaOssPreviewContextContractTest
        extends AbstractOssPreviewContextContractTest<JakartaOssPreviewContext> {

    @Override
    protected JakartaOssPreviewContext createPreviewContext(
            MockHttpServletRequest request, MockHttpServletResponse response) {
        return new JakartaOssPreviewContext(request, response);
    }
}
