package com.wiblog.oss.controller;

import com.wiblog.oss.controller.support.JakartaOpenApiOssControllerSupport;
import com.wiblog.oss.service.OssTemplate;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring Boot 3.x 带文档能力的 OSS HTTP 端点公开类型。
 *
 * <p>Boot3 的 OpenAPI 文档控制器逻辑已经收敛到共享 `jakarta` 支持模块，
 * 这里仅保留版本公开类型与类级元数据。</p>
 *
 * @author panwm
 */
@Validated
@Tag(name = "OSS 对象存储接口")
@RestController
@RequestMapping("${oss.http.prefix:}/oss")
public class OpenApiOssController3 extends JakartaOpenApiOssControllerSupport {

    public OpenApiOssController3(OssTemplate ossTemplate) {
        super(ossTemplate);
    }
}
