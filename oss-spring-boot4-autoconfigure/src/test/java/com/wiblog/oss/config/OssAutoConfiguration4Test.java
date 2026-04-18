package com.wiblog.oss.config;

import com.wiblog.oss.service.OssTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OssAutoConfiguration4 测试。
 *
 * <p>Boot 4 的 Bean 注册策略属于自动装配模块职责，
 * 测试随实现迁移后，starter 可以退回到纯依赖聚合角色，
 * 不再为测试而间接耦合实现细节。</p>
 */
@DisplayName("OssAutoConfiguration4 自动配置")
class OssAutoConfiguration4Test {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OssAutoConfiguration4.class));

    @Test
    @DisplayName("工厂方法应只创建 OssTemplate")
    void factoryMethodCreatesTemplate() {
        OssAutoConfiguration4 configuration = new OssAutoConfiguration4();
        OssProperties4 properties = new OssProperties4();
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("ak");
        properties.setSecretKey("sk");
        properties.setBucketName("");
        properties.setType("minio");
        properties.setAutoCreateBucket(false);

        OssTemplate template = configuration.ossTemplate(properties);
        try {
            assertThat(template).isNotNull();
        } finally {
            template.stop();
        }
    }

    @Test
    @DisplayName("启用 OSS 时应注册 OssTemplate")
    void registersTemplateWhenOssEnabled() {
        contextRunner
                .withPropertyValues(
                        "oss.enable=true",
                        "oss.endpoint=http://localhost:9000",
                        "oss.access-key=ak",
                        "oss.secret-key=sk",
                        "oss.type=minio",
                        "oss.bucket-name=",
                        "oss.auto-create-bucket=false")
                .run(context -> assertThat(context).hasSingleBean(OssTemplate.class));
    }

    @Test
    @DisplayName("未启用 OSS 时不应注册 OssTemplate")
    void doesNotRegisterTemplateWhenOssDisabled() {
        contextRunner
                .withPropertyValues("oss.enable=false")
                .run(context -> assertThat(context).doesNotHaveBean(OssTemplate.class));
    }
}
