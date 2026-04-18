package com.wiblog.oss.controller;

import com.wiblog.oss.controller.support.JakartaOssControllerSupport;
import com.wiblog.oss.service.OssTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring Boot 4.x OSS HTTP 端点公开类型。
 *
 * <p>Boot4 继续复用共享 `jakarta` 控制器实现，
 * 这里只保留版本公开类型与 Bean 暴露入口，避免对外类名漂移。</p>
 *
 * @author panwm
 */
@Validated
@RestController
@RequestMapping("${oss.http.prefix:}/oss")
public class OssController4 extends JakartaOssControllerSupport {

    public OssController4(OssTemplate ossTemplate) {
        super(ossTemplate);
    }
}
