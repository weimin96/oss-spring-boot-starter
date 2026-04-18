package com.wiblog.oss.controller;

import com.wiblog.oss.bean.*;
import com.wiblog.oss.bean.chunk.ChunkMerge;
import com.wiblog.oss.bean.chunk.ChunkPartInfo;
import com.wiblog.oss.bean.chunk.ChunkTarget;
import com.wiblog.oss.bean.chunk.ChunkTask;
import com.wiblog.oss.bean.chunk.ChunkUploadCommand;
import com.wiblog.oss.resp.OssResponse;
import com.wiblog.oss.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * OssController2 单元测试。
 *
 * <p>Boot2 控制器与 Boot3/Boot4 一样是薄委派层，
 * 这里直接覆盖参数解析、通配路径提取和返回包装，避免覆盖率被重复样板方法拖低。</p>
 */
@DisplayName("OssController2 单元测试")
class OssController2UnitTest {

    private OssTemplate ossTemplate;
    private PutOperations putOperations;
    private QueryOperations queryOperations;
    private DeleteOperations deleteOperations;
    private StreamUnzipOperations unzipOperations;
    private PresignOperations presignOperations;
    private TaggingOperations taggingOperations;
    private BucketOperations bucketOperations;
    private OssController2 controller;

    @BeforeEach
    void setUp() {
        ossTemplate = mock(OssTemplate.class);
        putOperations = mock(PutOperations.class);
        queryOperations = mock(QueryOperations.class);
        deleteOperations = mock(DeleteOperations.class);
        unzipOperations = mock(StreamUnzipOperations.class);
        presignOperations = mock(PresignOperations.class);
        taggingOperations = mock(TaggingOperations.class);
        bucketOperations = mock(BucketOperations.class);

        when(ossTemplate.put()).thenReturn(putOperations);
        when(ossTemplate.query()).thenReturn(queryOperations);
        when(ossTemplate.delete()).thenReturn(deleteOperations);
        when(ossTemplate.unzip()).thenReturn(unzipOperations);
        when(ossTemplate.presign()).thenReturn(presignOperations);
        when(ossTemplate.tagging()).thenReturn(taggingOperations);
        when(ossTemplate.bucket()).thenReturn(bucketOperations);
        when(ossTemplate.getDefaultBucketName()).thenReturn("unit-bucket");

        controller = new OssController2(ossTemplate);
    }

    @Nested
    @DisplayName("分片与上传")
    class UploadApiTest {

        @Test
        @DisplayName("分片相关接口应正确委派给 PutOperations")
        void multipartEndpointsDelegateToPutOperations() {
            ChunkTask chunkTask = new ChunkTask();
            MockMultipartFile chunkFile = new MockMultipartFile(
                    "file", "file.txt", "text/plain", "chunk-data".getBytes(StandardCharsets.UTF_8));
            ChunkMerge chunkMerge = new ChunkMerge();
            ChunkTarget chunkTarget = new ChunkTarget();
            chunkTarget.setPartNumber(1);
            chunkTarget.setEtag("etag-1");
            ObjectInfo mergedObject = ObjectInfo.builder().name("merged.txt").uri("path/merged.txt").build();
            ChunkPartInfo partInfo = new ChunkPartInfo();
            partInfo.setPartNumber(1);
            partInfo.setEtag("etag-1");
            partInfo.setSize(10L);
            List<ChunkPartInfo> parts = Collections.singletonList(partInfo);

            when(putOperations.initTask(chunkTask)).thenReturn("upload-id");
            when(putOperations.chunk(any(ChunkUploadCommand.class))).thenReturn(chunkTarget);
            when(putOperations.merge(chunkMerge)).thenReturn(mergedObject);
            when(putOperations.listParts("unit-bucket", "path/file.txt", "upload-id")).thenReturn(parts);

            assertThat(controller.initTask(chunkTask).getData()).isEqualTo("upload-id");
            assertThat(controller.chunk(1, "file.txt", "path", "guid-1", chunkFile, "upload-id").getData())
                    .isSameAs(chunkTarget);
            assertThat(controller.merge(chunkMerge).getData()).isSameAs(mergedObject);
            assertThat(controller.listParts("path/file.txt", "upload-id").getData()).isSameAs(parts);

            verify(putOperations).initTask(chunkTask);
            ArgumentCaptor<ChunkUploadCommand> chunkCommandCaptor = ArgumentCaptor.forClass(ChunkUploadCommand.class);
            verify(putOperations).chunk(chunkCommandCaptor.capture());
            verify(putOperations).merge(chunkMerge);
            verify(putOperations).listParts("unit-bucket", "path/file.txt", "upload-id");

            ChunkUploadCommand chunkCommand = chunkCommandCaptor.getValue();
            assertThat(chunkCommand.getChunkNumber()).isEqualTo(1);
            assertThat(chunkCommand.getFilename()).isEqualTo("file.txt");
            assertThat(chunkCommand.getPath()).isEqualTo("path");
            assertThat(chunkCommand.getGuid()).isEqualTo("guid-1");
            assertThat(chunkCommand.getUploadId()).isEqualTo("upload-id");
            assertThat(chunkCommand.getContentLength()).isEqualTo("chunk-data".getBytes(StandardCharsets.UTF_8).length);
            assertThat(chunkCommand.getFileBytes()).isEqualTo("chunk-data".getBytes(StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("上传对象时应根据 filename 是否为空选择文件名")
        void uploadObjectResolvesFilename() throws IOException {
            MockMultipartFile multipartFile = new MockMultipartFile(
                    "file", "origin.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
            ObjectInfo originResult = ObjectInfo.builder().name("origin.txt").uri("demo/origin.txt").build();
            ObjectInfo customResult = ObjectInfo.builder().name("custom.txt").uri("demo/custom.txt").build();

            when(putOperations.putObject(eq("demo"), eq("origin.txt"), any(InputStream.class))).thenReturn(originResult);
            when(putOperations.putObject(eq("demo"), eq("custom.txt"), any(InputStream.class))).thenReturn(customResult);

            OssResponse<ObjectInfo> originResponse = controller.uploadObject(multipartFile, "demo", " ");
            OssResponse<ObjectInfo> customResponse = controller.uploadObject(multipartFile, "demo", "custom.txt");

            assertThat(originResponse.getData()).isSameAs(originResult);
            assertThat(customResponse.getData()).isSameAs(customResult);

            verify(putOperations).putObject(eq("demo"), eq("origin.txt"), any(InputStream.class));
            verify(putOperations).putObject(eq("demo"), eq("custom.txt"), any(InputStream.class));
        }

        @Test
        @DisplayName("创建文件夹接口应返回 PutOperations 结果")
        void createFolderDelegates() {
            ObjectInfo folderInfo = ObjectInfo.builder().name("demo").uri("demo/").build();
            when(putOperations.mkdirs("demo")).thenReturn(folderInfo);

            assertThat(controller.createFolder("demo").getData()).isSameAs(folderInfo);

            verify(putOperations).mkdirs("demo");
        }
    }

    @Nested
    @DisplayName("删除与查询")
    class QueryApiTest {

        @Test
        @DisplayName("删除接口应调用 DeleteOperations")
        void deleteEndpointsDelegate() {
            List<String> objectNames = Arrays.asList("a.txt", "b.txt");

            OssResponse<Void> deleteObjectResponse = controller.deleteObject("a.txt");
            OssResponse<Void> deleteObjectsResponse = controller.deleteObjects(objectNames);
            OssResponse<Void> deleteFolderResponse = controller.deleteFolder("demo/");

            assertThat(deleteObjectResponse.isSuccess()).isTrue();
            assertThat(deleteObjectsResponse.isSuccess()).isTrue();
            assertThat(deleteFolderResponse.isSuccess()).isTrue();

            verify(deleteOperations).removeObject("a.txt");
            verify(deleteOperations).removeObjects(objectNames);
            verify(deleteOperations).removeFolder("demo/");
        }

        @Test
        @DisplayName("查询接口应原样返回 QueryOperations 结果")
        void queryEndpointsDelegate() {
            ObjectInfo objectInfo = ObjectInfo.builder().name("file.txt").uri("demo/file.txt").build();
            ObjectTreeNode folderNode = new ObjectTreeNode("demo", "demo", "url", null, "folder", 0, null);
            List<ObjectInfo> objectInfos = Collections.singletonList(objectInfo);
            List<ObjectTreeNode> treeNodes = Collections.singletonList(folderNode);
            LazyDataList<ObjectInfo> lazyResult = new LazyDataList<ObjectInfo>()
                    .setMaxKeys(20)
                    .setContinuationToken("next-token")
                    .setRecords(objectInfos);

            when(queryOperations.getObjectInfo("demo/file.txt")).thenReturn(objectInfo);
            when(queryOperations.checkExist("demo/file.txt")).thenReturn(true);
            when(queryOperations.listObjects("demo")).thenReturn(objectInfos);
            when(queryOperations.listNextLevel("demo")).thenReturn(treeNodes);
            when(queryOperations.lazyList("demo", 20, "token-1")).thenReturn(lazyResult);
            when(queryOperations.getTreeList("demo")).thenReturn(folderNode);
            when(queryOperations.getTreeListByName("demo", "file")).thenReturn(folderNode);
            when(queryOperations.getFolderTreeList("demo")).thenReturn(treeNodes);
            when(queryOperations.getAllBuckets()).thenReturn(
                    Collections.singletonList(BucketInfo.builder().name("unit-bucket").build()));
            when(queryOperations.testConnect()).thenReturn(true);

            assertThat(controller.getObject("demo/file.txt").getData()).isSameAs(objectInfo);
            assertThat(controller.objectExists("demo/file.txt").getData()).isTrue();
            assertThat(controller.listObjects("demo").getData()).isSameAs(objectInfos);
            assertThat(controller.listNextLevel("demo").getData()).isSameAs(treeNodes);
            assertThat(controller.lazyList("demo", 20, "token-1").getData()).isSameAs(lazyResult);
            assertThat(controller.getObjectTree("demo").getData()).isSameAs(folderNode);
            assertThat(controller.searchObjectTree("demo", "file").getData()).isSameAs(folderNode);
            assertThat(controller.getFolderTree("demo").getData()).isSameAs(treeNodes);
            assertThat(controller.listBuckets().getData()).hasSize(1);
            assertThat(controller.testConnect().getData()).isTrue();
        }
    }

    @Nested
    @DisplayName("预览与文件操作")
    class PreviewAndFileOperationTest {

        @Test
        @DisplayName("预览与下载接口应解析通配路径并传递下载标记")
        void previewAndDownloadDelegate() throws Exception {
            MockHttpServletRequest previewRequest = new MockHttpServletRequest("GET", "/oss/object/preview/demo/a.txt");
            previewRequest.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "/oss/object/preview/demo/a.txt");
            previewRequest.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/oss/object/preview/**");

            MockHttpServletRequest downloadRequest = new MockHttpServletRequest("GET", "/oss/object/download/demo/a.txt");
            downloadRequest.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "/oss/object/download/demo/a.txt");
            downloadRequest.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/oss/object/download/**");

            controller.previewObject(new MockHttpServletResponse(), previewRequest);
            controller.downloadObject(new MockHttpServletResponse(), downloadRequest);

            verify(queryOperations).previewObject(any(), eq("demo/a.txt"), eq(false));
            verify(queryOperations).previewObject(any(), eq("demo/a.txt"), eq(true));
        }

        @Test
        @DisplayName("复制、移动与预签名接口应正确委派")
        void copyMoveAndPresignDelegate() {
            when(presignOperations.generateGetPresignedUrl("demo/file.txt", Duration.ofSeconds(60)))
                    .thenReturn("get-url");
            when(presignOperations.generatePutPresignedUrl(
                    "demo/file.txt", "text/plain", Duration.ofSeconds(120), null))
                    .thenReturn("put-url");

            OssResponse<Void> copyResponse = controller.copyObject("demo/file.txt", "backup/file.txt");
            OssResponse<Void> moveResponse = controller.moveObject("backup/file.txt", "archive");
            OssResponse<String> getPresignResponse = controller.getPresignedUrl("demo/file.txt", 60);
            OssResponse<String> putPresignResponse = controller.putPresignedUrl("demo/file.txt", "text/plain", 120);

            assertThat(copyResponse.getMsg()).isEqualTo("复制成功");
            assertThat(moveResponse.getMsg()).isEqualTo("移动成功");
            assertThat(getPresignResponse.getData()).isEqualTo("get-url");
            assertThat(putPresignResponse.getData()).isEqualTo("put-url");

            verify(putOperations).copyFile("demo/file.txt", "backup/file.txt");
            verify(putOperations).move("backup/file.txt", "archive");
            verify(presignOperations).generateGetPresignedUrl("demo/file.txt", Duration.ofSeconds(60));
            verify(presignOperations).generatePutPresignedUrl(
                    "demo/file.txt", "text/plain", Duration.ofSeconds(120), null);
        }
    }

    @Nested
    @DisplayName("解压与标签")
    class UnzipAndTagTest {

        @Test
        @DisplayName("解压接口应返回解压结果")
        void unzipEndpointsDelegate() {
            UnzipResult unzipResult = UnzipResult.builder()
                    .succeeded(Collections.singletonList(
                            ObjectInfo.builder().name("a.txt").uri("demo/unzip/a.txt").build()))
                    .failed(Collections.emptyList())
                    .targetPath("demo/unzip/")
                    .build();

            when(unzipOperations.unzip("zip/demo.zip", "demo/unzip")).thenReturn(unzipResult);
            when(unzipOperations.unzip("source-bucket", "zip/demo.zip", "target-bucket", "demo/unzip"))
                    .thenReturn(unzipResult);
            when(unzipOperations.unzipWithFilter("zip/demo.zip", "folder/", "demo/unzip")).thenReturn(unzipResult);

            assertThat(controller.unzip("zip/demo.zip", "demo/unzip").getData()).isSameAs(unzipResult);
            assertThat(controller.unzipCrossBucket("source-bucket", "zip/demo.zip", "target-bucket", "demo/unzip").getData())
                    .isSameAs(unzipResult);
            assertThat(controller.unzipWithFilter("zip/demo.zip", "folder/", "demo/unzip").getData()).isSameAs(unzipResult);
        }

        @Test
        @DisplayName("对象标签与 Bucket 标签接口应委派到 TaggingOperations")
        void taggingEndpointsDelegate() {
            Map<String, String> objectTags = Collections.singletonMap("env", "test");
            Map<String, String> bucketTags = Collections.singletonMap("owner", "team");

            when(taggingOperations.getObjectTags("demo/file.txt")).thenReturn(objectTags);
            when(taggingOperations.getBucketTags()).thenReturn(bucketTags);

            assertThat(controller.getObjectTags("demo/file.txt").getData()).isSameAs(objectTags);
            assertThat(controller.getBucketTags().getData()).isSameAs(bucketTags);
            assertThat(controller.setObjectTags("demo/file.txt", objectTags).isSuccess()).isTrue();
            assertThat(controller.mergeObjectTags("demo/file.txt", objectTags).isSuccess()).isTrue();
            assertThat(controller.deleteObjectTags("demo/file.txt").isSuccess()).isTrue();
            assertThat(controller.setBucketTags(bucketTags).isSuccess()).isTrue();
            assertThat(controller.deleteBucketTags().isSuccess()).isTrue();

            verify(taggingOperations).setObjectTags("demo/file.txt", objectTags);
            verify(taggingOperations).mergeObjectTags("demo/file.txt", objectTags);
            verify(taggingOperations).deleteObjectTags("demo/file.txt");
            verify(taggingOperations).setBucketTags(bucketTags);
            verify(taggingOperations).deleteBucketTags();
        }
    }

    @Nested
    @DisplayName("Bucket 管理")
    class BucketApiTest {

        @Test
        @DisplayName("指定 Bucket 资源接口应返回 BucketOperations 结果")
        void bucketResourceEndpointsDelegate() {
            BucketDetailInfo detailInfo = BucketDetailInfo.builder().name("unit-bucket").access("private").build();
            BucketAccessInfo accessInfo = BucketAccessInfo.builder()
                    .bucketName("unit-bucket")
                    .acl("private")
                    .supported(true)
                    .build();
            BucketRewindResult rewindResult = BucketRewindResult.builder()
                    .bucketName("unit-bucket")
                    .targetTime("2026-04-17T12:00:00Z")
                    .build();

            when(bucketOperations.getBucketDetail("unit-bucket")).thenReturn(detailInfo);
            when(bucketOperations.getBucketAccess("unit-bucket")).thenReturn(accessInfo);
            when(bucketOperations.setBucketAccess("unit-bucket", "private")).thenReturn(accessInfo);
            when(bucketOperations.rewindBucket("unit-bucket", "2026-04-17T12:00:00Z")).thenReturn(rewindResult);

            assertThat(controller.getBucketDetail("unit-bucket").getData()).isSameAs(detailInfo);
            assertThat(controller.getBucketAccess("unit-bucket").getData()).isSameAs(accessInfo);
            assertThat(controller.setBucketAccess("unit-bucket", "private").getData()).isSameAs(accessInfo);
            assertThat(controller.rewindBucket("unit-bucket", "2026-04-17T12:00:00Z").getData()).isSameAs(rewindResult);
        }

        @Test
        @DisplayName("默认 Bucket 管理接口应委派到 BucketOperations 与 TaggingOperations")
        void bucketAdminEndpointsDelegate() {
            LifecycleRuleInfo lifecycleRuleInfo = LifecycleRuleInfo.builder().id("rule-1").build();
            CorsRuleInfo corsRuleInfo = CorsRuleInfo.builder().id("cors-1").build();

            when(bucketOperations.getVersioningStatus()).thenReturn("Enabled");
            when(bucketOperations.getLifecycleRules()).thenReturn(Collections.singletonList(lifecycleRuleInfo));
            when(bucketOperations.getCorsRules()).thenReturn(Collections.singletonList(corsRuleInfo));
            when(bucketOperations.getBucketPolicy()).thenReturn("{\"Statement\":[]}");

            assertThat(controller.createBucket("new-bucket").isSuccess()).isTrue();
            assertThat(controller.getVersioningStatus().getData()).isEqualTo("Enabled");
            assertThat(controller.enableVersioning().getMsg()).isEqualTo("版本控制已启用");
            assertThat(controller.suspendVersioning().getMsg()).isEqualTo("版本控制已挂起");
            assertThat(controller.getLifecycleRules().getData()).hasSize(1);
            assertThat(controller.deleteLifecycleRules().isSuccess()).isTrue();
            assertThat(controller.addExpirationRule("rule-1", "archive/", 7).isSuccess()).isTrue();
            assertThat(controller.getCorsRules().getData()).hasSize(1);
            assertThat(controller.allowAllOriginsCors().isSuccess()).isTrue();
            assertThat(controller.deleteCorsRules().isSuccess()).isTrue();
            assertThat(controller.getBucketPolicy().getData()).contains("Statement");
            assertThat(controller.putBucketPolicy("{\"Statement\":[]}").isSuccess()).isTrue();
            assertThat(controller.deleteBucketPolicy().isSuccess()).isTrue();
            assertThat(controller.enableEncryption().isSuccess()).isTrue();
            assertThat(controller.blockAllPublicAccess().isSuccess()).isTrue();

            verify(putOperations).createBucket("new-bucket");
            verify(bucketOperations).enableVersioning();
            verify(bucketOperations).suspendVersioning();
            verify(bucketOperations).deleteLifecycleRules();
            verify(bucketOperations).addExpirationRule("rule-1", "archive/", 7);
            verify(bucketOperations).allowAllOriginsCors();
            verify(bucketOperations).deleteCorsRules();
            verify(bucketOperations).putBucketPolicy("{\"Statement\":[]}");
            verify(bucketOperations).deleteBucketPolicy();
            verify(bucketOperations).enableServerSideEncryption();
            verify(bucketOperations).blockAllPublicAccess();
        }
    }
}
