package com.wiblog.oss.support;

import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.exception.OssException;
import com.wiblog.oss.service.OssTemplate;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Service 测试基类。
 *
 * <p>统一通过 `DynamicPropertyRegistry` 注入 MinIO 连接信息，避免每个测试类单独维护配置文件或
 * 独立的容器生命周期。</p>
 */
@SpringBootTest(classes = TestApplication.class)
public abstract class AbstractServiceDynamicPropertyTest {

    @Autowired
    protected OssTemplate ossTemplate;

    @Autowired
    protected OssProperties ossProperties;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        MinioTestSupport.registerServiceProperties(registry);
    }

    @AfterEach
    void cleanupBucketObjects() {
        try {
            ossTemplate.delete().removeFolder("service-tests/");
        } catch (OssException exception) {
            if (!"OBJECT_NOT_FOUND".equals(exception.getCode())) {
                throw exception;
            }
        }
    }

    protected String newTestDirectory() {
        return "service-tests/" + UUID.randomUUID();
    }

    protected String putTextObject(String directory, String filename, String content) {
        ossTemplate.put().putObjectForKey(
                directory + "/" + filename,
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))
        );
        return directory + "/" + filename;
    }
}
