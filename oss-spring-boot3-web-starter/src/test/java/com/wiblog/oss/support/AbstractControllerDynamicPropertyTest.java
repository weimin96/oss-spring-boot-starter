package com.wiblog.oss.support;

import com.wiblog.oss.exception.OssException;
import com.wiblog.oss.service.OssTemplate;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Controller 测试基类。
 *
 * <p>这里开启真实 Web 上下文并通过 `MockMvc` 调用接口，是为了让控制器、异常处理器和 Starter 自动配置
 * 在同一条链路上被验证。</p>
 */
@AutoConfigureMockMvc
@SpringBootTest(classes = TestApplication.class)
public abstract class AbstractControllerDynamicPropertyTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected OssTemplate ossTemplate;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        MinioTestSupport.registerControllerProperties(registry);
    }

    @AfterEach
    void cleanupBucketObjects() {
        try {
            ossTemplate.delete().removeFolder("controller-tests/");
        } catch (OssException exception) {
            if (!"OBJECT_NOT_FOUND".equals(exception.getCode())) {
                throw exception;
            }
        }
    }

    protected String newControllerDirectory() {
        return "controller-tests/" + UUID.randomUUID();
    }

    protected String putControllerTextObject(String directory, String filename, String content) {
        ossTemplate.put().putObjectForKey(
                directory + "/" + filename,
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))
        );
        return directory + "/" + filename;
    }
}
