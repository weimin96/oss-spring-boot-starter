package com.wiblog.oss.controller;

import com.wiblog.oss.controller.support.JakartaOssControllerSupport;
import com.wiblog.oss.service.OssTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring Boot 3.x OSS HTTP 端点公开类型。
 *
 * <p>控制器逻辑已经沉到共享 `jakarta` 支持模块，
 * 这里保留对外类名与运行时注册入口，降低模块重组的破坏性。</p>
 *
 * @author panwm
 */
@Validated
@RestController
@RequestMapping("${oss.http.prefix:}/oss")
public class OssController3 extends JakartaOssControllerSupport {

    public OssController3(OssTemplate ossTemplate) {
        super(ossTemplate);
    }
}
