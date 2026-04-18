package com.wiblog.oss.controller;

import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.bean.chunk.ChunkTask;
import com.wiblog.oss.resp.OssResponse;
import com.wiblog.oss.support.AbstractControllerDynamicPropertyTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OssController 集成测试。
 *
 */
@DisplayName("OssController 集成测试")
class OssControllerIntegrationTest extends AbstractControllerDynamicPropertyTest {

    @Nested
    @DisplayName("对象查询与上传")
    class ObjectApiTest {

        @Test
        @DisplayName("上传后应能通过详情、列表与树接口读到对象")
        void uploadAndQueryObject() throws Exception {
            String directory = newControllerDirectory();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "hello.txt", "text/plain", "controller-content".getBytes(StandardCharsets.UTF_8));

            mockMvc.perform(multipart("/oss/object")
                            .file(file)
                            .param("path", directory))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("hello.txt"));

            mockMvc.perform(get("/oss/object")
                            .param("objectName", directory + "/hello.txt"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.uri").value(directory + "/hello.txt"));

            mockMvc.perform(get("/oss/object/list")
                            .param("path", directory))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));

            mockMvc.perform(get("/oss/object/tree")
                            .param("path", directory))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.type").value("folder"));
        }

        @Test
        @DisplayName("未指定 filename 时应回退到原始文件名")
        void useOriginalFilenameWhenFilenameMissing() throws Exception {
            String directory = newControllerDirectory();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "original.txt", "text/plain", "original".getBytes(StandardCharsets.UTF_8));

            mockMvc.perform(multipart("/oss/object")
                            .file(file)
                            .param("path", directory))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("original.txt"));
        }
    }

    @Nested
    @DisplayName("删除接口")
    class DeleteApiTest {

        @Test
        @DisplayName("删除对象后再次查询应返回空数据")
        void deleteObject() throws Exception {
            String directory = newControllerDirectory();
            String objectKey = putControllerTextObject(directory, "delete.txt", "delete");

            mockMvc.perform(delete("/oss/object")
                            .param("objectName", objectKey))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            mockMvc.perform(get("/oss/object")
                            .param("objectName", objectKey))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("删除目录后目录下对象应全部消失")
        void deleteFolder() throws Exception {
            String directory = newControllerDirectory();
            putControllerTextObject(directory, "a.txt", "a");
            putControllerTextObject(directory + "/nested", "b.txt", "b");

            mockMvc.perform(delete("/oss/folder")
                            .param("path", directory + "/"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            assertThat(ossTemplate.query().listObjects(directory)).isEmpty();
        }
    }

    @Nested
    @DisplayName("分片接口")
    class ChunkApiTest {

        @Test
        @DisplayName("初始化分片任务应返回 uploadId")
        void initTask() throws Exception {
            mockMvc.perform(post("/oss/multipart/init")
                            .param("path", newControllerDirectory())
                            .param("filename", "big.txt")
                            .param("guid", "guid-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isNotEmpty());
        }

        @Test
        @DisplayName("上传分片后应返回分片号与 etag")
        void chunkUpload() throws Exception {
            String directory = newControllerDirectory();
            ChunkTask chunkTask = new ChunkTask();
            chunkTask.setPath(directory);
            chunkTask.setFilename("chunk.txt");
            String uploadId = ossTemplate.put().initTask(chunkTask);

            MockMultipartFile chunkFile = new MockMultipartFile(
                    "file", "chunk.txt", "text/plain", "chunk-data".getBytes(StandardCharsets.UTF_8));

            mockMvc.perform(multipart("/oss/multipart/chunk")
                            .file(chunkFile)
                            .param("chunkNumber", "1")
                            .param("filename", "chunk.txt")
                            .param("path", directory)
                            .param("guid", "guid-1")
                            .param("uploadId", uploadId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.partNumber").value(1))
                    .andExpect(jsonPath("$.data.etag").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("辅助对象")
    class SupportObjectTest {

        @Test
        @DisplayName("R.data(null) 应返回无数据提示")
        void responseWrapper() {
            OssResponse<String> result = OssResponse.data(null);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMsg()).isEqualTo("暂无承载数据");
        }

        @Test
        @DisplayName("OssProperties 默认值应保持稳定")
        void propertiesDefaults() {
            OssProperties properties = new OssProperties();

            assertThat(properties.getThroughputInGbps()).isEqualTo(20.0);
            assertThat(properties.getPartSizeInMb()).isEqualTo(10);
            assertThat(properties.getMaxConnections()).isEqualTo(50);
            assertThat(properties.getConnectionTimeout()).isEqualTo(10_000L);
            assertThat(properties.getHttp().isEnable()).isFalse();
        }
    }
}
