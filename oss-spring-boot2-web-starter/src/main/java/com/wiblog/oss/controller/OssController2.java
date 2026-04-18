package com.wiblog.oss.controller;

import com.wiblog.oss.controller.support.JavaxOssControllerSupport;
import com.wiblog.oss.service.OssTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring Boot 2.x OSS HTTP 端点公开类型。
 *
 * <p>运行时逻辑已经沉到 `oss-spring-javax-web-support`，
 * 这里保留稳定公开类型，避免用户自定义扩展或测试代码感知内部模块重组。</p>
 *
 * @author panwm
 */
@Validated
@RestController
@RequestMapping("${oss.http.prefix:}/oss")
public class OssController2 extends JavaxOssControllerSupport {

    public OssController2(OssTemplate ossTemplate) {
        super(ossTemplate);
    }
}
