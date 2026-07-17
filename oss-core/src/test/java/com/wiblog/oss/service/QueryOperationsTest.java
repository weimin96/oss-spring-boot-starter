package com.wiblog.oss.service;

import com.wiblog.oss.bean.LazyDataList;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ReadObjectRangeCommand;
import com.wiblog.oss.bean.StoredObject;
import com.wiblog.oss.config.OssClientOptions;
import com.wiblog.oss.exception.OssException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
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
    void typedRangeMethodRemainsDefaultInterfaceAdapter() throws NoSuchMethodException {
        assertEquals(true, OssQueryService.class
                .getMethod("getInputStream", ReadObjectRangeCommand.class)
                .isDefault());
    }

    @Test
    void headObjectReturnsStableStorageMetadata() {
        List<HeadObjectRequest> requests = new ArrayList<>();
        QueryOperations operations = operations(s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("headObject".equals(methodName)) {
                    requests.add((HeadObjectRequest) args[0]);
                    return completed(HeadObjectResponse.builder()
                            .contentLength(42L)
                            .eTag("etag-1")
                            .versionId("version-1")
                            .checksumSHA256("checksum-1")
                            .build());
                }
                throw unsupported(methodName);
            }
        }));

        StoredObject result = operations.headObject("bucket", "/docs/readme.txt");

        assertEquals("docs/readme.txt", requests.get(0).key());
        assertEquals(ChecksumMode.ENABLED, requests.get(0).checksumMode());
        assertEquals(new StoredObject("bucket", "docs/readme.txt", 42L,
                "etag-1", "version-1", "checksum-1"), result);
    }

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
    void getInputStreamUsesBlockingStreamTransformer() throws IOException {
        QueryOperations operations = operations(s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("getObject".equals(methodName)) {
                    return completed(new ResponseInputStream<GetObjectResponse>(
                            GetObjectResponse.builder().build(),
                            new ByteArrayInputStream("streamed".getBytes(StandardCharsets.UTF_8))));
                }
                throw unsupported(methodName);
            }
        }));

        InputStream inputStream = operations.getInputStream("bucket", "large-object");

        assertEquals("streamed", readString(inputStream));
    }

    @Test
    void getInputStreamWithRangeUsesBlockingStreamTransformer() throws IOException {
        List<GetObjectRequest> requests = new ArrayList<>();
        QueryOperations operations = operations(s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("getObject".equals(methodName)) {
                    requests.add((GetObjectRequest) args[0]);
                    return completed(new ResponseInputStream<GetObjectResponse>(
                            GetObjectResponse.builder().build(),
                            new ByteArrayInputStream("part".getBytes(StandardCharsets.UTF_8))));
                }
                throw unsupported(methodName);
            }
        }));

        InputStream inputStream = operations.getInputStream("bucket", "large-object", "bytes=0-3");

        assertEquals("part", readString(inputStream));
        assertEquals("bytes=0-3", requests.get(0).range());
    }

    @Test
    void getInputStreamWithTypedRangeBuildsBoundedRequest() throws IOException {
        List<GetObjectRequest> requests = new ArrayList<>();
        QueryOperations operations = operations(s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("getObject".equals(methodName)) {
                    requests.add((GetObjectRequest) args[0]);
                    return completed(new ResponseInputStream<GetObjectResponse>(
                            GetObjectResponse.builder().build(),
                            new ByteArrayInputStream("part".getBytes(StandardCharsets.UTF_8))));
                }
                throw unsupported(methodName);
            }
        }));

        InputStream inputStream = operations.getInputStream(
                new ReadObjectRangeCommand(null, "/large-object", 8L, 4L));

        assertEquals("part", readString(inputStream));
        assertEquals("bucket", requests.get(0).bucket());
        assertEquals("large-object", requests.get(0).key());
        assertEquals("bytes=8-11", requests.get(0).range());
    }

    @Test
    void getInputStreamWithTypedRangeRejectsInvalidBounds() {
        QueryOperations operations = operations(s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                throw unsupported(methodName);
            }
        }));

        assertThrows(IllegalArgumentException.class, () -> operations.getInputStream(
                new ReadObjectRangeCommand("bucket", "object", -1L, 1L)));
        assertThrows(IllegalArgumentException.class, () -> operations.getInputStream(
                new ReadObjectRangeCommand("bucket", "object", 0L, 0L)));
        assertThrows(IllegalArgumentException.class, () -> operations.getInputStream(
                new ReadObjectRangeCommand("bucket", "object", Long.MAX_VALUE, 2L)));
    }

    @Test
    void getInputStreamMapsMissingObjectToDomainError() {
        QueryOperations operations = operations(s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("getObject".equals(methodName)) {
                    return failed(NoSuchKeyException.builder().message("missing").build());
                }
                throw unsupported(methodName);
            }
        }));

        OssException failure = assertThrows(OssException.class,
                () -> operations.getInputStream("bucket", "missing.txt"));

        assertEquals("OBJECT_NOT_FOUND", failure.getCode());
    }

    @Test
    void getInputStreamMapsMissingBucketToDomainError() {
        QueryOperations operations = operations(s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("getObject".equals(methodName)) {
                    return failed(NoSuchBucketException.builder().message("missing bucket").build());
                }
                throw unsupported(methodName);
            }
        }));

        OssException failure = assertThrows(OssException.class,
                () -> operations.getInputStream("missing-bucket", "object"));

        assertEquals("BUCKET_NOT_FOUND", failure.getCode());
    }

    @Test
    void getInputStreamMapsUnsatisfiedRangeToDomainError() {
        QueryOperations operations = operations(s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("getObject".equals(methodName)) {
                    return failed(S3Exception.builder()
                            .statusCode(416)
                            .awsErrorDetails(AwsErrorDetails.builder()
                                    .errorCode("InvalidRange")
                                    .errorMessage("invalid range")
                                    .build())
                            .build());
                }
                throw unsupported(methodName);
            }
        }));

        OssException failure = assertThrows(OssException.class, () -> operations.getInputStream(
                new ReadObjectRangeCommand("bucket", "object", 100L, 10L)));

        assertEquals("OBJECT_RANGE_NOT_SATISFIABLE", failure.getCode());
    }

    @Test
    void getInputStreamMapsAccessDeniedToDomainError() {
        QueryOperations operations = operations(s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("getObject".equals(methodName)) {
                    return failed(S3Exception.builder()
                            .statusCode(403)
                            .awsErrorDetails(AwsErrorDetails.builder()
                                    .errorCode("AccessDenied")
                                    .errorMessage("denied")
                                    .build())
                            .build());
                }
                throw unsupported(methodName);
            }
        }));

        OssException failure = assertThrows(OssException.class,
                () -> operations.getInputStream("bucket", "object"));

        assertEquals("OBJECT_READ_FORBIDDEN", failure.getCode());
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
                    return completed(new ResponseInputStream<GetObjectResponse>(
                            GetObjectResponse.builder().build(),
                            new ByteArrayInputStream("world".getBytes(StandardCharsets.UTF_8))));
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

    private static String readString(InputStream inputStream) throws IOException {
        try (InputStream in = inputStream;
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8];
            int read;
            while ((read = in.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        }
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
