package com.wiblog.oss.controller;

import com.wiblog.oss.service.OssTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * web端点
 * @author panwm
 * @date 2023/8/20 1:38
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("${oss.http.prefix:}/oss")
public class OssController {

    /**
     * OSS操作模板
     */
    private final OssTemplate ossTemplate;
}
