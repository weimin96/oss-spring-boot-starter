package com.wiblog.oss.service;

import com.wiblog.oss.bean.OssObjectEvent;
import com.wiblog.oss.exception.OssException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MinIO 对象事件解析测试。
 */
@DisplayName("MinIO 对象事件解析")
class MinioObjectEventParserTest {

    private final MinioObjectEventParser parser = new MinioObjectEventParser();

    @Test
    @DisplayName("应解析标准 S3 事件结构")
    void parsesStandardEventRecord() {
        String json = "{"
                + "\"Records\":[{"
                + "\"eventName\":\"s3:ObjectCreated:Put\","
                + "\"eventTime\":\"2026-05-14T10:00:00Z\","
                + "\"s3\":{"
                + "\"bucket\":{\"name\":\"demo-bucket\"},"
                + "\"object\":{"
                + "\"key\":\"images%2Fhello+world.jpg\","
                + "\"size\":1024,"
                + "\"eTag\":\"etag-1\","
                + "\"versionId\":\"v1\","
                + "\"sequencer\":\"seq-1\""
                + "}"
                + "}"
                + "}]"
                + "}";

        List<OssObjectEvent> events = parser.parse(json);

        assertThat(events).hasSize(1);
        OssObjectEvent event = events.get(0);
        assertThat(event.getBucketName()).isEqualTo("demo-bucket");
        assertThat(event.getObjectKey()).isEqualTo("images/hello world.jpg");
        assertThat(event.getEventName()).isEqualTo("s3:ObjectCreated:Put");
        assertThat(event.getEventTime()).isEqualTo("2026-05-14T10:00:00Z");
        assertThat(event.getSize()).isEqualTo(1024L);
        assertThat(event.getETag()).isEqualTo("etag-1");
        assertThat(event.getVersionId()).isEqualTo("v1");
        assertThat(event.getSequencer()).isEqualTo("seq-1");
    }

    @Test
    @DisplayName("空事件行应返回空列表")
    void blankLineReturnsEmptyEvents() {
        assertThat(parser.parse(" ")).isEmpty();
    }

    @Test
    @DisplayName("畸形 JSON 应显式抛出解析异常")
    void malformedJsonThrowsException() {
        assertThatThrownBy(() -> parser.parse("{"))
                .isInstanceOf(OssException.class)
                .hasMessageContaining("解析对象事件 JSON 失败");
    }

    @Test
    @DisplayName("缺少 Records 应显式抛出解析异常")
    void missingRecordsThrowsException() {
        assertThatThrownBy(() -> parser.parse("{}"))
                .isInstanceOf(OssException.class)
                .hasMessageContaining("对象事件缺少 Records 数组");
    }

    @Test
    @DisplayName("缺少对象键的记录应跳过")
    void missingObjectKeyIsSkipped() {
        String json = "{\"Records\":[{\"s3\":{\"bucket\":{\"name\":\"demo-bucket\"},\"object\":{}}}]}";

        assertThat(parser.parse(json)).isEmpty();
    }
}
