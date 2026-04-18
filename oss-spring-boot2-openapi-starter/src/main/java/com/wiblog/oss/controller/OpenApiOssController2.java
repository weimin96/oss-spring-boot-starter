package com.wiblog.oss.controller;

import com.wiblog.oss.controller.support.JavaxOpenApiOssControllerSupport;
import com.wiblog.oss.service.OssTemplate;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring Boot 2.x 带文档能力的 OSS HTTP 端点公开类型。
 *
 * <p>文档控制器逻辑已经收敛到 `javax` 共享支持模块，
 * 这里保留公开类名、类级路由与 Swagger 标签，避免对外兼容面破坏。</p>
 *
 * @author panwm
 */
@Validated
@Tag(name = "OSS 对象存储接口")
@RestController
@RequestMapping("${oss.http.prefix:}/oss")
public class OpenApiOssController2 extends JavaxOpenApiOssControllerSupport {

    public OpenApiOssController2(OssTemplate ossTemplate) {
        super(ossTemplate);
    }
}
