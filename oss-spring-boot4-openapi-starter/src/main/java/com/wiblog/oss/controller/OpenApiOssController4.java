package com.wiblog.oss.controller;

import com.wiblog.oss.controller.support.JakartaOpenApiOssControllerSupport;
import com.wiblog.oss.service.OssTemplate;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring Boot 4.x 带文档能力的 OSS HTTP 端点公开类型。
 *
 * <p>Boot4 继续复用共享 `jakarta` 文档控制器实现，
 * 版本模块只保留公开类型、路由前缀和 Swagger 标签暴露。</p>
 *
 * @author panwm
 */
@Validated
@Tag(name = "OSS 对象存储接口")
@RestController
@RequestMapping("${oss.http.prefix:}/oss")
public class OpenApiOssController4 extends JakartaOpenApiOssControllerSupport {

    public OpenApiOssController4(OssTemplate ossTemplate) {
        super(ossTemplate);
    }
}
