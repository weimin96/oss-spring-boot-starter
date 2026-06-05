package com.wiblog.oss.service;

import com.wiblog.oss.config.OssClientOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 下载链路单元测试。
 *
 * <p>这组测试直接覆盖 core 查询实现，是为了避免无后缀对象再次被误判为目录前缀。
 * Boot4 下载端点只是薄包装，实际对象流读取和 Range 处理都在 {@link QueryOperations} 中完成。</p>
 */
@DisplayName("QueryOperations 下载链路")
class QueryOperationsDownloadUnitTest {

    @Test
    @DisplayName("下载无后缀对象时应使用精确对象 key")
    void downloadObjectWithoutExtensionUsesExactObjectKey() throws Exception {
        S3AsyncClient client = mock(S3AsyncClient.class);
        byte[] objectBytes = "no-extension-content".getBytes(StandardCharsets.UTF_8);
        stubObjectInfo(client, "reports/archive", objectBytes.length);
        stubObjectContent(client, objectBytes);
        QueryOperations operations = newQueryOperations(client);
        RecordingPreviewContext context = new RecordingPreviewContext("GET", null);

        operations.previewObject(context, "reports/archive", true);

        GetObjectRequest request = verifySingleGetObjectRequest(client);
        assertThat(request.key()).isEqualTo("reports/archive");
        assertThat(request.range()).isNull();
        assertThat(context.getStatus()).isEqualTo(200);
        assertThat(context.getContentLength()).isEqualTo(objectBytes.length);
        assertThat(context.bodyAsText()).isEqualTo("no-extension-content");
        assertThat(context.getHeader("Content-Disposition")).contains("filename=\"archive\"");
    }

    @Test
    @DisplayName("Range 下载无后缀对象时应使用精确对象 key")
    void rangeDownloadObjectWithoutExtensionUsesExactObjectKey() throws Exception {
        S3AsyncClient client = mock(S3AsyncClient.class);
        byte[] objectBytes = "range-content".getBytes(StandardCharsets.UTF_8);
        stubObjectInfo(client, "reports/archive", objectBytes.length);
        stubObjectContent(client, objectBytes);
        QueryOperations operations = newQueryOperations(client);
        RecordingPreviewContext context = new RecordingPreviewContext("GET", "bytes=0-4");

        operations.previewObject(context, "reports/archive", true);

        GetObjectRequest request = verifySingleGetObjectRequest(client);
        assertThat(request.key()).isEqualTo("reports/archive");
        assertThat(request.range()).isEqualTo("bytes=0-4");
        assertThat(context.getStatus()).isEqualTo(206);
        assertThat(context.getHeader("Content-Length")).isEqualTo("5");
        assertThat(context.getHeader("Content-Range")).isEqualTo("bytes 0-4/" + objectBytes.length);
        assertThat(context.bodyAsText()).isEqualTo("range");
    }

    @Test
    @DisplayName("对象流缺失时下载链路应返回 404")
    void missingObjectStreamReturnsNotFound() throws Exception {
        S3AsyncClient client = mock(S3AsyncClient.class);
        stubObjectInfo(client, "reports/archive", 7L);
        stubObjectMissing(client);
        QueryOperations operations = newQueryOperations(client);
        RecordingPreviewContext context = new RecordingPreviewContext("GET", null);

        operations.previewObject(context, "reports/archive", true);

        assertThat(context.getStatus()).isEqualTo(404);
        assertThat(context.bodyAsText()).contains("404 Not Found");
    }

    private static QueryOperations newQueryOperations(S3AsyncClient client) {
        OssClientOptions options = new OssClientOptions(
                "http://localhost:9000", "access-key", "secret-key", "minio", "unit-bucket");
        return new QueryOperations(options, client, mock(S3TransferManager.class));
    }

    private static void stubObjectInfo(S3AsyncClient client, String objectKey, long objectSize) {
        HeadObjectResponse response = HeadObjectResponse.builder()
                .contentLength(objectSize)
                .lastModified(Instant.parse("2026-04-18T00:00:00Z"))
                .build();
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenAnswer(invocation -> {
                    HeadObjectRequest request = invocation.getArgument(0);
                    assertThat(request.key()).isEqualTo(objectKey);
                    return CompletableFuture.completedFuture(response);
                });
    }

    private static void stubObjectContent(S3AsyncClient client, byte[] objectBytes) {
        ResponseInputStream<GetObjectResponse> responseInputStream = new ResponseInputStream<>(
                GetObjectResponse.builder().build(), new ByteArrayInputStream(objectBytes));
        CompletableFuture<ResponseInputStream<GetObjectResponse>> future =
                CompletableFuture.completedFuture(responseInputStream);
        doReturn(future).when(client).getObject(
                any(GetObjectRequest.class), anyBlockingInputStreamTransformer());
    }

    private static void stubObjectMissing(S3AsyncClient client) {
        CompletableFuture<ResponseInputStream<GetObjectResponse>> future = new CompletableFuture<>();
        future.completeExceptionally(NoSuchKeyException.builder().message("missing").build());
        doReturn(future).when(client).getObject(
                any(GetObjectRequest.class), anyBlockingInputStreamTransformer());
    }

    private static GetObjectRequest verifySingleGetObjectRequest(S3AsyncClient client) {
        org.mockito.ArgumentCaptor<GetObjectRequest> requestCaptor =
                org.mockito.ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(client).getObject(requestCaptor.capture(), anyBlockingInputStreamTransformer());
        return requestCaptor.getValue();
    }

    private static AsyncResponseTransformer<GetObjectResponse, ResponseInputStream<GetObjectResponse>>
    anyBlockingInputStreamTransformer() {
        return any();
    }

    private static final class RecordingPreviewContext implements OssPreviewContext {

        private final String method;
        private final String rangeHeader;
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private final Map<String, String> headers = new LinkedHashMap<>();
        private int status = 200;
        private long contentLength = -1L;
        private String contentType;

        private RecordingPreviewContext(String method, String rangeHeader) {
            this.method = method;
            this.rangeHeader = rangeHeader;
        }

        @Override
        public String getMethod() {
            return method;
        }

        @Override
        public String getRangeHeader() {
            return rangeHeader;
        }

        @Override
        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        @Override
        public void setHeader(String name, String value) {
            headers.put(name, value);
        }

        @Override
        public void setStatus(int statusCode) {
            this.status = statusCode;
        }

        @Override
        public void setContentLengthLong(long length) {
            this.contentLength = length;
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return responseBody;
        }

        private int getStatus() {
            return status;
        }

        private long getContentLength() {
            return contentLength;
        }

        private String getHeader(String name) {
            return headers.get(name);
        }

        private String bodyAsText() {
            return new String(responseBody.toByteArray(), StandardCharsets.UTF_8);
        }

        @SuppressWarnings("unused")
        private String getContentType() {
            return contentType;
        }
    }
}
