package com.wiblog.oss.service;

import com.wiblog.oss.bean.LazyDataList;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.config.OssClientOptions;
import com.wiblog.oss.exception.OssException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueryOperationsTest {

    @Test
    void lazyListCapsMaxKeysAtS3Limit() {
        List<ListObjectsV2Request> requests = new ArrayList<>();
        QueryOperations operations = operations(s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("listObjectsV2".equals(methodName)) {
                    requests.add((ListObjectsV2Request) args[0]);
                    return completed(ListObjectsV2Response.builder().build());
                }
                throw unsupported(methodName);
            }
        }));

        LazyDataList<ObjectInfo> result = operations.lazyList("bucket", "docs", 9999, "token");

        assertEquals(1, requests.size());
        assertEquals(1000, requests.get(0).maxKeys());
        assertEquals(1000, result.getMaxKeys());
    }

    @Test
    void getContentReturnsNullWhenAsyncGetObjectReportsMissingKey() {
        QueryOperations operations = operations(s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("getObject".equals(methodName)) {
                    return failed(NoSuchKeyException.builder().message("missing").build());
                }
                throw unsupported(methodName);
            }
        }));

        assertNull(operations.getContent("bucket", "missing.txt"));
    }

    @Test
    void getFileFailsWhenRemoteDownloadDoesNotSucceed() {
        QueryOperations operations = operations(s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("getObject".equals(methodName)) {
                    return failed(S3Exception.builder().message("denied").build());
                }
                throw unsupported(methodName);
            }
        }));

        assertThrows(OssException.class, () -> operations.getFile("bucket", "missing.txt", "missing.txt"));
    }

    @Test
    void previewObjectSupportsSuffixRange() throws IOException {
        List<GetObjectRequest> requests = new ArrayList<>();
        QueryOperations operations = operations(s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("headObject".equals(methodName)) {
                    return completed(HeadObjectResponse.builder()
                            .contentLength(10L)
                            .lastModified(Instant.parse("2026-06-05T00:00:00Z"))
                            .build());
                }
                if ("getObject".equals(methodName)) {
                    GetObjectRequest request = (GetObjectRequest) args[0];
                    requests.add(request);
                    ResponseBytes<GetObjectResponse> bytes = ResponseBytes.fromByteArray(
                            GetObjectResponse.builder().build(),
                            "world".getBytes(StandardCharsets.UTF_8));
                    return completed(bytes);
                }
                throw unsupported(methodName);
            }
        }));
        FakePreviewContext context = new FakePreviewContext("bytes=-5");

        operations.previewObject(context, "hello.txt", false);

        assertEquals(206, context.statusCode);
        assertEquals("bytes 5-9/10", context.header("Content-Range"));
        assertEquals("5", context.header("Content-Length"));
        assertEquals("world", context.body());
        assertEquals("bytes=5-9", requests.get(0).range());
    }

    @Test
    void previewObjectRejectsInvalidRange() throws IOException {
        QueryOperations operations = operations(s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("headObject".equals(methodName)) {
                    return completed(HeadObjectResponse.builder()
                            .contentLength(10L)
                            .lastModified(Instant.parse("2026-06-05T00:00:00Z"))
                            .build());
                }
                if ("getObject".equals(methodName)) {
                    throw new AssertionError("invalid range should not request object content");
                }
                throw unsupported(methodName);
            }
        }));
        FakePreviewContext context = new FakePreviewContext("bytes=abc");

        operations.previewObject(context, "hello.txt", false);

        assertEquals(416, context.statusCode);
        assertEquals("bytes */10", context.header("Content-Range"));
        assertEquals("0", context.header("Content-Length"));
        assertEquals("", context.body());
    }

    private static QueryOperations operations(S3AsyncClient client) {
        OssClientOptions options = new OssClientOptions("http://localhost:9000", "access-key", "secret-key", "minio", "bucket");
        return new QueryOperations(options, client, null);
    }

    private static S3AsyncClient s3Client(S3Handler handler) {
        return (S3AsyncClient) Proxy.newProxyInstance(
                S3AsyncClient.class.getClassLoader(),
                new Class<?>[]{S3AsyncClient.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("serviceName".equals(methodName)) {
                        return "s3";
                    }
                    if ("close".equals(methodName)) {
                        return null;
                    }
                    return handler.handle(methodName, args);
                });
    }

    private static CompletableFuture<Object> completed(Object value) {
        return CompletableFuture.completedFuture(value);
    }

    private static CompletableFuture<Object> failed(Throwable throwable) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }

    private static UnsupportedOperationException unsupported(String methodName) {
        return new UnsupportedOperationException(methodName);
    }

    private interface S3Handler {
        CompletableFuture<?> handle(String methodName, Object[] args);
    }

    private static final class FakePreviewContext implements OssPreviewContext {
        private final String rangeHeader;
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private final List<String[]> headers = new ArrayList<>();
        private int statusCode = 200;

        private FakePreviewContext(String rangeHeader) {
            this.rangeHeader = rangeHeader;
        }

        @Override
        public String getMethod() {
            return "GET";
        }

        @Override
        public String getRangeHeader() {
            return rangeHeader;
        }

        @Override
        public void setContentType(String contentType) {
            setHeader("Content-Type", contentType);
        }

        @Override
        public void setHeader(String name, String value) {
            headers.add(new String[]{name, value});
        }

        @Override
        public void setStatus(int statusCode) {
            this.statusCode = statusCode;
        }

        @Override
        public void setContentLengthLong(long length) {
            setHeader("Content-Length", String.valueOf(length));
        }

        @Override
        public OutputStream getOutputStream() {
            return outputStream;
        }

        private String header(String name) {
            for (String[] header : headers) {
                if (name.equals(header[0])) {
                    return header[1];
                }
            }
            return null;
        }

        private String body() {
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
