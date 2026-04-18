package com.wiblog.oss.contract;

import com.wiblog.oss.bean.BucketAccessInfo;
import com.wiblog.oss.bean.BucketDetailInfo;
import com.wiblog.oss.bean.BucketInfo;
import com.wiblog.oss.bean.BucketRewindResult;
import com.wiblog.oss.bean.CorsRuleInfo;
import com.wiblog.oss.bean.LazyDataList;
import com.wiblog.oss.bean.LifecycleRuleInfo;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;
import com.wiblog.oss.bean.UnzipResult;
import com.wiblog.oss.bean.chunk.ChunkMerge;
import com.wiblog.oss.bean.chunk.ChunkPartInfo;
import com.wiblog.oss.bean.chunk.ChunkTarget;
import com.wiblog.oss.bean.chunk.ChunkTask;
import com.wiblog.oss.bean.chunk.ChunkUploadCommand;
import com.wiblog.oss.resp.OssResponse;
import com.wiblog.oss.service.BucketOperations;
import com.wiblog.oss.service.DeleteOperations;
import com.wiblog.oss.service.OssTemplate;
import com.wiblog.oss.service.PresignOperations;
import com.wiblog.oss.service.PutOperations;
import com.wiblog.oss.service.QueryOperations;
import com.wiblog.oss.service.StreamUnzipOperations;
import com.wiblog.oss.service.TaggingOperations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OSS 控制器契约测试。
 *
 * <p>Boot2、Boot3、Boot4 的公开控制器类型不同，
 * 但它们都只是共享实现外包的一层薄包装。把这组测试沉到公共模块后，
 * 可以把控制器对外语义固定在一处，只让各版本本地测试负责提供具体类型。</p>
 *
 * @param <T> 控制器公开类型
 * @author panwm
 */
public abstract class AbstractOssControllerContractTest<T> {

    protected OssTemplate ossTemplate;
    protected PutOperations putOperations;
    protected QueryOperations queryOperations;
    protected DeleteOperations deleteOperations;
    protected StreamUnzipOperations unzipOperations;
    protected PresignOperations presignOperations;
    protected TaggingOperations taggingOperations;
    protected BucketOperations bucketOperations;
    protected T controller;

    /**
     * 创建具体版本的控制器公开类型。
     *
     * @param ossTemplate 模板门面
     * @return 控制器实例
     */
    protected abstract T createController(OssTemplate ossTemplate);

    @BeforeEach
    void setUpControllerContract() {
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

        controller = createController(ossTemplate);
    }

    @Nested
    @DisplayName("分片与上传")
    class UploadApiTest {

        @Test
        @DisplayName("分片相关接口应正确委派给 PutOperations")
        void multipartEndpointsDelegateToPutOperations() throws Exception {
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

            assertThat(invokeResponse("initTask", new Class<?>[]{ChunkTask.class}, chunkTask).getData())
                    .isEqualTo("upload-id");
            assertThat(invokeResponse("chunk", new Class<?>[]{
                    Integer.class, String.class, String.class, String.class, MultipartFile.class, String.class
            }, 1, "file.txt", "path", "guid-1", chunkFile, "upload-id").getData()).isSameAs(chunkTarget);
            assertThat(invokeResponse("merge", new Class<?>[]{ChunkMerge.class}, chunkMerge).getData())
                    .isSameAs(mergedObject);
            assertThat(invokeResponse("listParts", new Class<?>[]{String.class, String.class},
                    "path/file.txt", "upload-id").getData()).isSameAs(parts);

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
        void uploadObjectResolvesFilename() throws Exception {
            MockMultipartFile multipartFile = new MockMultipartFile(
                    "file", "origin.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
            ObjectInfo originResult = ObjectInfo.builder().name("origin.txt").uri("demo/origin.txt").build();
            ObjectInfo customResult = ObjectInfo.builder().name("custom.txt").uri("demo/custom.txt").build();

            when(putOperations.putObject(eq("demo"), eq("origin.txt"), any(InputStream.class))).thenReturn(originResult);
            when(putOperations.putObject(eq("demo"), eq("custom.txt"), any(InputStream.class))).thenReturn(customResult);

            OssResponse<ObjectInfo> originResponse = invokeResponse(
                    "uploadObject", new Class<?>[]{MultipartFile.class, String.class, String.class},
                    multipartFile, "demo", " ");
            OssResponse<ObjectInfo> customResponse = invokeResponse(
                    "uploadObject", new Class<?>[]{MultipartFile.class, String.class, String.class},
                    multipartFile, "demo", "custom.txt");

            assertThat(originResponse.getData()).isSameAs(originResult);
            assertThat(customResponse.getData()).isSameAs(customResult);

            verify(putOperations).putObject(eq("demo"), eq("origin.txt"), any(InputStream.class));
            verify(putOperations).putObject(eq("demo"), eq("custom.txt"), any(InputStream.class));
        }

        @Test
        @DisplayName("创建文件夹接口应返回 PutOperations 结果")
        void createFolderDelegates() throws Exception {
            ObjectInfo folderInfo = ObjectInfo.builder().name("demo").uri("demo/").build();
            when(putOperations.mkdirs("demo")).thenReturn(folderInfo);

            assertThat(invokeResponse("createFolder", new Class<?>[]{String.class}, "demo").getData())
                    .isSameAs(folderInfo);

            verify(putOperations).mkdirs("demo");
        }
    }

    @Nested
    @DisplayName("删除与查询")
    class QueryApiTest {

        @Test
        @DisplayName("删除接口应调用 DeleteOperations")
        void deleteEndpointsDelegate() throws Exception {
            List<String> objectNames = Arrays.asList("a.txt", "b.txt");

            OssResponse<Void> deleteObjectResponse = invokeResponse("deleteObject", new Class<?>[]{String.class}, "a.txt");
            OssResponse<Void> deleteObjectsResponse = invokeResponse(
                    "deleteObjects", new Class<?>[]{List.class}, objectNames);
            OssResponse<Void> deleteFolderResponse = invokeResponse("deleteFolder", new Class<?>[]{String.class}, "demo/");

            assertThat(deleteObjectResponse.isSuccess()).isTrue();
            assertThat(deleteObjectsResponse.isSuccess()).isTrue();
            assertThat(deleteFolderResponse.isSuccess()).isTrue();

            verify(deleteOperations).removeObject("a.txt");
            verify(deleteOperations).removeObjects(objectNames);
            verify(deleteOperations).removeFolder("demo/");
        }

        @Test
        @DisplayName("查询接口应原样返回 QueryOperations 结果")
        void queryEndpointsDelegate() throws Exception {
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

            assertThat(invokeResponse("getObject", new Class<?>[]{String.class}, "demo/file.txt").getData())
                    .isSameAs(objectInfo);
            assertThat(invokeResponse("objectExists", new Class<?>[]{String.class}, "demo/file.txt").getData())
                    .isEqualTo(Boolean.TRUE);
            assertThat(invokeResponse("listObjects", new Class<?>[]{String.class}, "demo").getData())
                    .isSameAs(objectInfos);
            assertThat(invokeResponse("listNextLevel", new Class<?>[]{String.class}, "demo").getData())
                    .isSameAs(treeNodes);
            assertThat(invokeResponse("lazyList", new Class<?>[]{String.class, int.class, String.class},
                    "demo", 20, "token-1").getData()).isSameAs(lazyResult);
            assertThat(invokeResponse("getObjectTree", new Class<?>[]{String.class}, "demo").getData())
                    .isSameAs(folderNode);
            assertThat(invokeResponse("searchObjectTree", new Class<?>[]{String.class, String.class},
                    "demo", "file").getData()).isSameAs(folderNode);
            assertThat(invokeResponse("getFolderTree", new Class<?>[]{String.class}, "demo").getData())
                    .isSameAs(treeNodes);
            assertThat(((List<?>) invokeResponse("listBuckets", new Class<?>[0]).getData())).hasSize(1);
            assertThat(invokeResponse("testConnect", new Class<?>[0]).getData()).isEqualTo(Boolean.TRUE);
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

            invokeServletEndpoint("previewObject", new MockHttpServletResponse(), previewRequest);
            invokeServletEndpoint("downloadObject", new MockHttpServletResponse(), downloadRequest);

            verify(queryOperations).previewObject(any(), eq("demo/a.txt"), eq(false));
            verify(queryOperations).previewObject(any(), eq("demo/a.txt"), eq(true));
        }

        @Test
        @DisplayName("复制、移动与预签名接口应正确委派")
        void copyMoveAndPresignDelegate() throws Exception {
            when(presignOperations.generateGetPresignedUrl("demo/file.txt", Duration.ofSeconds(60)))
                    .thenReturn("get-url");
            when(presignOperations.generatePutPresignedUrl(
                    "demo/file.txt", "text/plain", Duration.ofSeconds(120), null))
                    .thenReturn("put-url");

            OssResponse<Void> copyResponse = invokeResponse(
                    "copyObject", new Class<?>[]{String.class, String.class}, "demo/file.txt", "backup/file.txt");
            OssResponse<Void> moveResponse = invokeResponse(
                    "moveObject", new Class<?>[]{String.class, String.class}, "backup/file.txt", "archive");
            OssResponse<String> getPresignResponse = invokeResponse(
                    "getPresignedUrl", new Class<?>[]{String.class, long.class}, "demo/file.txt", 60L);
            OssResponse<String> putPresignResponse = invokeResponse(
                    "putPresignedUrl", new Class<?>[]{String.class, String.class, long.class},
                    "demo/file.txt", "text/plain", 120L);

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
        void unzipEndpointsDelegate() throws Exception {
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

            assertThat(invokeResponse("unzip", new Class<?>[]{String.class, String.class},
                    "zip/demo.zip", "demo/unzip").getData()).isSameAs(unzipResult);
            assertThat(invokeResponse("unzipCrossBucket", new Class<?>[]{
                    String.class, String.class, String.class, String.class
            }, "source-bucket", "zip/demo.zip", "target-bucket", "demo/unzip").getData()).isSameAs(unzipResult);
            assertThat(invokeResponse("unzipWithFilter", new Class<?>[]{
                    String.class, String.class, String.class
            }, "zip/demo.zip", "folder/", "demo/unzip").getData()).isSameAs(unzipResult);
        }

        @Test
        @DisplayName("对象标签与 Bucket 标签接口应委派到 TaggingOperations")
        void taggingEndpointsDelegate() throws Exception {
            Map<String, String> objectTags = Collections.singletonMap("env", "test");
            Map<String, String> bucketTags = Collections.singletonMap("owner", "team");

            when(taggingOperations.getObjectTags("demo/file.txt")).thenReturn(objectTags);
            when(taggingOperations.getBucketTags()).thenReturn(bucketTags);

            assertThat(invokeResponse("getObjectTags", new Class<?>[]{String.class}, "demo/file.txt").getData())
                    .isSameAs(objectTags);
            assertThat(invokeResponse("getBucketTags", new Class<?>[0]).getData()).isSameAs(bucketTags);
            assertThat(invokeResponse("setObjectTags", new Class<?>[]{String.class, Map.class},
                    "demo/file.txt", objectTags).isSuccess()).isTrue();
            assertThat(invokeResponse("mergeObjectTags", new Class<?>[]{String.class, Map.class},
                    "demo/file.txt", objectTags).isSuccess()).isTrue();
            assertThat(invokeResponse("deleteObjectTags", new Class<?>[]{String.class}, "demo/file.txt").isSuccess()).isTrue();
            assertThat(invokeResponse("setBucketTags", new Class<?>[]{Map.class}, bucketTags).isSuccess()).isTrue();
            assertThat(invokeResponse("deleteBucketTags", new Class<?>[0]).isSuccess()).isTrue();

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
        void bucketResourceEndpointsDelegate() throws Exception {
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

            assertThat(invokeResponse("getBucketDetail", new Class<?>[]{String.class}, "unit-bucket").getData())
                    .isSameAs(detailInfo);
            assertThat(invokeResponse("getBucketAccess", new Class<?>[]{String.class}, "unit-bucket").getData())
                    .isSameAs(accessInfo);
            assertThat(invokeResponse("setBucketAccess", new Class<?>[]{String.class, String.class},
                    "unit-bucket", "private").getData()).isSameAs(accessInfo);
            assertThat(invokeResponse("rewindBucket", new Class<?>[]{String.class, String.class},
                    "unit-bucket", "2026-04-17T12:00:00Z").getData()).isSameAs(rewindResult);
        }

        @Test
        @DisplayName("默认 Bucket 管理接口应委派到 BucketOperations 与 TaggingOperations")
        void bucketAdminEndpointsDelegate() throws Exception {
            LifecycleRuleInfo lifecycleRuleInfo = LifecycleRuleInfo.builder().id("rule-1").build();
            CorsRuleInfo corsRuleInfo = CorsRuleInfo.builder().id("cors-1").build();

            when(bucketOperations.getVersioningStatus()).thenReturn("Enabled");
            when(bucketOperations.getLifecycleRules()).thenReturn(Collections.singletonList(lifecycleRuleInfo));
            when(bucketOperations.getCorsRules()).thenReturn(Collections.singletonList(corsRuleInfo));
            when(bucketOperations.getBucketPolicy()).thenReturn("{\"Statement\":[]}");

            assertThat(invokeResponse("createBucket", new Class<?>[]{String.class}, "new-bucket").isSuccess()).isTrue();
            assertThat(invokeResponse("getVersioningStatus", new Class<?>[0]).getData()).isEqualTo("Enabled");
            assertThat(invokeResponse("enableVersioning", new Class<?>[0]).getMsg()).isEqualTo("版本控制已启用");
            assertThat(invokeResponse("suspendVersioning", new Class<?>[0]).getMsg()).isEqualTo("版本控制已挂起");
            assertThat(((List<?>) invokeResponse("getLifecycleRules", new Class<?>[0]).getData())).hasSize(1);
            assertThat(invokeResponse("deleteLifecycleRules", new Class<?>[0]).isSuccess()).isTrue();
            assertThat(invokeResponse("addExpirationRule", new Class<?>[]{String.class, String.class, int.class},
                    "rule-1", "archive/", 7).isSuccess()).isTrue();
            assertThat(((List<?>) invokeResponse("getCorsRules", new Class<?>[0]).getData())).hasSize(1);
            assertThat(invokeResponse("allowAllOriginsCors", new Class<?>[0]).isSuccess()).isTrue();
            assertThat(invokeResponse("deleteCorsRules", new Class<?>[0]).isSuccess()).isTrue();
            assertThat(String.valueOf(invokeResponse("getBucketPolicy", new Class<?>[0]).getData())).contains("Statement");
            assertThat(invokeResponse("putBucketPolicy", new Class<?>[]{String.class}, "{\"Statement\":[]}").isSuccess()).isTrue();
            assertThat(invokeResponse("deleteBucketPolicy", new Class<?>[0]).isSuccess()).isTrue();
            assertThat(invokeResponse("enableEncryption", new Class<?>[0]).isSuccess()).isTrue();
            assertThat(invokeResponse("blockAllPublicAccess", new Class<?>[0]).isSuccess()).isTrue();

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

    @SuppressWarnings("unchecked")
    private <R> OssResponse<R> invokeResponse(String methodName, Class<?>[] parameterTypes, Object... arguments)
            throws Exception {
        return (OssResponse<R>) invokeControllerMethod(controller.getClass().getMethod(methodName, parameterTypes), arguments);
    }

    private void invokeServletEndpoint(String methodName, Object... arguments) throws Exception {
        invokeControllerMethod(findServletEndpoint(methodName), arguments);
    }

    private Method findServletEndpoint(String methodName) throws NoSuchMethodException {
        Method[] methods = controller.getClass().getMethods();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (method.getName().equals(methodName) && method.getParameterTypes().length == 2) {
                return method;
            }
        }
        throw new NoSuchMethodException(methodName);
    }

    private Object invokeControllerMethod(Method method, Object... arguments) throws Exception {
        try {
            return method.invoke(controller, arguments);
        } catch (InvocationTargetException ex) {
            Throwable targetException = ex.getTargetException();
            if (targetException instanceof Exception) {
                throw (Exception) targetException;
            }
            if (targetException instanceof Error) {
                throw (Error) targetException;
            }
            throw ex;
        }
    }
}
