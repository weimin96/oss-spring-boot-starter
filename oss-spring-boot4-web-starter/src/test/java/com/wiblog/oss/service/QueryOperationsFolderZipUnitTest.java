package com.wiblog.oss.service;

import com.wiblog.oss.config.OssClientOptions;
import com.wiblog.oss.exception.OssException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 文件夹流式 ZIP 下载单元测试。
 *
 * <p>这组测试直接覆盖 core 层，是为了锁定 prefix 列举、目录占位过滤和 ZIP 写出行为。
 * Web 控制器只负责响应头和错误协议，真正的 ZIP 组装逻辑必须由 {@link QueryOperations} 保持稳定。</p>
 */
@DisplayName("QueryOperations 文件夹 ZIP 导出")
class QueryOperationsFolderZipUnitTest {

    @Test
    @DisplayName("应按相对路径写入 ZIP 并跳过目录占位对象")
    void writeFolderAsZipCreatesRelativeEntries() throws Exception {
        S3AsyncClient client = mock(S3AsyncClient.class);
        QueryOperations operations = spy(newQueryOperations(client));
        List<S3Object> objects = Arrays.asList(
                folderPlaceholder("reports/"),
                fileObject("reports/a.txt", 5L),
                fileObject("reports/nested/b.txt", 4L),
                fileObject("reports/empty.bin", 0L));
        doReturn(objects).when(operations).listObject("unit-bucket", "reports/", null);
        Map<String, byte[]> objectBodies = new LinkedHashMap<>();
        objectBodies.put("reports/a.txt", "alpha".getBytes(StandardCharsets.UTF_8));
        objectBodies.put("reports/nested/b.txt", "beta".getBytes(StandardCharsets.UTF_8));
        objectBodies.put("reports/empty.bin", new byte[0]);
        stubBlockingObjectStreams(client, objectBodies);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        operations.writeFolderAsZip("reports", outputStream);

        Map<String, String> zipEntries = readZipEntries(outputStream.toByteArray());
        assertThat(zipEntries).containsEntry("a.txt", "alpha");
        assertThat(zipEntries).containsEntry("nested/b.txt", "beta");
        assertThat(zipEntries).containsEntry("empty.bin", "");
        assertThat(zipEntries).doesNotContainKey("");
        verify(operations).listObject("unit-bucket", "reports/", null);
    }

    @Test
    @DisplayName("没有真实对象时应显式失败而不是返回空 ZIP")
    void writeFolderAsZipFailsWhenNoRealObjectsExist() {
        QueryOperations operations = spy(newQueryOperations(mock(S3AsyncClient.class)));
        doReturn(Arrays.asList(folderPlaceholder("reports/")))
                .when(operations).listObject("unit-bucket", "reports/", null);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        assertThatThrownBy(() -> operations.writeFolderAsZip("reports/", outputStream))
                .isInstanceOf(OssException.class)
                .hasMessageContaining("未找到前缀下的对象");
        assertThat(outputStream.toByteArray()).isEmpty();
    }

    @Test
    @DisplayName("对象流无法打开时应显式失败且不写出空 ZIP")
    void writeFolderAsZipFailsWhenObjectStreamCannotOpen() {
        S3AsyncClient client = mock(S3AsyncClient.class);
        QueryOperations operations = spy(newQueryOperations(client));
        doReturn(Arrays.asList(fileObject("reports/a.txt", 5L)))
                .when(operations).listObject("unit-bucket", "reports/", null);
        stubBlockingObjectStreamMissing(client, "reports/a.txt");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        assertThatThrownBy(() -> operations.writeFolderAsZip("reports/", outputStream))
                .isInstanceOf(OssException.class)
                .hasMessageContaining("未找到对象：reports/a.txt");
        assertThat(outputStream.toByteArray()).isEmpty();
    }

    private static QueryOperations newQueryOperations(S3AsyncClient client) {
        OssClientOptions options = new OssClientOptions(
                "http://localhost:9000", "access-key", "secret-key", "minio", "unit-bucket");
        return new QueryOperations(options, client, mock(S3TransferManager.class));
    }

    private static S3Object folderPlaceholder(String key) {
        return S3Object.builder()
                .key(key)
                .size(0L)
                .lastModified(Instant.parse("2026-04-18T00:00:00Z"))
                .build();
    }

    private static S3Object fileObject(String key, long size) {
        return S3Object.builder()
                .key(key)
                .size(size)
                .lastModified(Instant.parse("2026-04-18T00:00:00Z"))
                .build();
    }

    private static void stubBlockingObjectStreams(S3AsyncClient client, Map<String, byte[]> objects) {
        doAnswer(invocation -> {
            GetObjectRequest request = invocation.getArgument(0);
            assertThat(objects).containsKey(request.key());
            ResponseInputStream<GetObjectResponse> responseInputStream = new ResponseInputStream<>(
                    GetObjectResponse.builder().build(),
                    new ByteArrayInputStream(objects.get(request.key())));
            return CompletableFuture.completedFuture(responseInputStream);
        }).when(client).getObject(any(GetObjectRequest.class), anyBlockingInputStreamTransformer());
    }

    private static void stubBlockingObjectStreamMissing(S3AsyncClient client, String expectedKey) {
        doAnswer(invocation -> {
            GetObjectRequest request = invocation.getArgument(0);
            assertThat(request.key()).isEqualTo(expectedKey);
            CompletableFuture<ResponseInputStream<GetObjectResponse>> future = new CompletableFuture<>();
            future.completeExceptionally(NoSuchKeyException.builder().message("missing").build());
            return future;
        }).when(client).getObject(any(GetObjectRequest.class), anyBlockingInputStreamTransformer());
    }

    private static AsyncResponseTransformer<GetObjectResponse, ResponseInputStream<GetObjectResponse>>
    anyBlockingInputStreamTransformer() {
        return any();
    }

    private static Map<String, String> readZipEntries(byte[] zipBytes) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        byte[] buffer = new byte[1024];
        try (ZipInputStream zipInputStream = new ZipInputStream(
                new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                ByteArrayOutputStream entryOutput = new ByteArrayOutputStream();
                int read;
                while ((read = zipInputStream.read(buffer)) != -1) {
                    entryOutput.write(buffer, 0, read);
                }
                entries.put(entry.getName(), new String(entryOutput.toByteArray(), StandardCharsets.UTF_8));
                zipInputStream.closeEntry();
            }
        }
        return entries;
    }
}
