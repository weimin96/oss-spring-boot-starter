package com.wiblog.oss.support;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * 测试专用启动配置。
 *
 * <p>保持最小自动配置范围，只让 Starter 自身能力参与装配，避免测试被应用侧额外 Bean 污染。</p>
 */
@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
public class TestApplication {
}
