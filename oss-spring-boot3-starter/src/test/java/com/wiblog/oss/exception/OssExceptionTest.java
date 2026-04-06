package com.wiblog.oss.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OssException} 单元测试
 */
@DisplayName("OssException 异常类")
class OssExceptionTest {

    @Nested
    @DisplayName("构造函数")
    class ConstructorTest {

        @Test
        @DisplayName("(code, message) 构造后字段正确")
        void twoArgConstructor() {
            OssException ex = new OssException("MY_CODE", "my message");
            assertThat(ex.getCode()).isEqualTo("MY_CODE");
            assertThat(ex.getMessage()).isEqualTo("my message");
            assertThat(ex.getCause()).isNull();
        }

        @Test
        @DisplayName("(code, message, cause) 构造后字段正确")
        void threeArgConstructor() {
            Throwable cause = new RuntimeException("root");
            OssException ex = new OssException("ERR", "wrapped", cause);
            assertThat(ex.getCode()).isEqualTo("ERR");
            assertThat(ex.getMessage()).isEqualTo("wrapped");
            assertThat(ex.getCause()).isSameAs(cause);
        }
    }

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethodTest {

        @Test
        @DisplayName("bucketNotFound 包含 bucket 名称和正确 code")
        void bucketNotFound() {
            OssException ex = OssException.bucketNotFound("my-bucket");
            assertThat(ex.getCode()).isEqualTo("BUCKET_NOT_FOUND");
            assertThat(ex.getMessage()).contains("my-bucket");
        }

        @Test
        @DisplayName("objectNotFound 包含 object 名称和正确 code")
        void objectNotFound() {
            OssException ex = OssException.objectNotFound("path/to/file.txt");
            assertThat(ex.getCode()).isEqualTo("OBJECT_NOT_FOUND");
            assertThat(ex.getMessage()).contains("path/to/file.txt");
        }

        @Test
        @DisplayName("uploadFailed 包含文件名和 cause")
        void uploadFailed() {
            Throwable cause = new RuntimeException("network error");
            OssException ex = OssException.uploadFailed("bigfile.zip", cause);
            assertThat(ex.getCode()).isEqualTo("UPLOAD_FAILED");
            assertThat(ex.getMessage()).contains("bigfile.zip");
            assertThat(ex.getCause()).isSameAs(cause);
        }

        @Test
        @DisplayName("configInvalid 包含字段名和正确 code")
        void configInvalid() {
            OssException ex = OssException.configInvalid("oss.endpoint");
            assertThat(ex.getCode()).isEqualTo("CONFIG_INVALID");
            assertThat(ex.getMessage()).contains("oss.endpoint");
        }
    }

    @Test
    @DisplayName("OssException 是 RuntimeException 的子类（不需要声明 throws）")
    void isRuntimeException() {
        assertThat(new OssException("C", "M")).isInstanceOf(RuntimeException.class);
    }
}
