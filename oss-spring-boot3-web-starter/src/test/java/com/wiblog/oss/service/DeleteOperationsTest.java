package com.wiblog.oss.service;

import com.wiblog.oss.support.AbstractServiceDynamicPropertyTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeleteOperations 集成测试。
 */
@DisplayName("DeleteOperations")
@ExtendWith(OutputCaptureExtension.class)
class DeleteOperationsTest extends AbstractServiceDynamicPropertyTest {

    @Test
    @DisplayName("删除单个对象后应无法再查询到")
    void removeObject() {
        String directory = newTestDirectory();
        String objectKey = putTextObject(directory, "single.txt", "single");

        ossTemplate.delete().removeObject(objectKey);

        assertThat(ossTemplate.query().checkExist(objectKey)).isFalse();
    }

    @Test
    @DisplayName("删除目录时应清理目录下全部对象")
    void removeFolder(CapturedOutput output) {
        String directory = newTestDirectory();
        String firstObject = putTextObject(directory, "a.txt", "a");
        String secondObject = putTextObject(directory + "/nested", "b.txt", "b");

        ossTemplate.delete().removeFolder(directory + "/");

        assertThat(ossTemplate.query().checkExist(firstObject)).isFalse();
        assertThat(ossTemplate.query().checkExist(secondObject)).isFalse();
        assertThat(output.getOut()).doesNotContain("Batch delete failed");
        assertThat(output.getOut()).doesNotContain("MissingContentMD5");
    }
}
