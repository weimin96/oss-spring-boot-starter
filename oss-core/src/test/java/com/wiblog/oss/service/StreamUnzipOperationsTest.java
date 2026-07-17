package com.wiblog.oss.service;

import com.wiblog.oss.bean.UnzipResult;
import com.wiblog.oss.config.OssClientOptions;
import com.wiblog.oss.exception.OssException;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StreamUnzipOperationsTest {

    @Test
    void unzipFailsWhenSourceZipCannotBeRead() {
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("getObject".equals(methodName)) {
                    return failed(S3Exception.builder().message("missing zip").build());
                }
                throw unsupported(methodName);
            }
        });

        assertThrows(OssException.class,
                () -> operations(client).unzip("bucket", "missing.zip", "bucket", "target"));
    }

    @Test
    void unzipRejectsParentDirectoryEntry() throws IOException {
        final byte[] archiveBytes = zipBytes("../escape.txt", "content");
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("getObject".equals(methodName)) {
                    return completed(new ResponseInputStream<GetObjectResponse>(
                            GetObjectResponse.builder().build(),
                            new ByteArrayInputStream(archiveBytes)));
                }
                if ("putObject".equals(methodName)) {
                    throw new AssertionError("非法 ZIP 条目不应被上传");
                }
                throw unsupported(methodName);
            }
        });

        OssException failure = assertThrows(OssException.class,
                () -> operations(client).unzip("bucket", "archive.zip", "bucket", "target"));

        assertEquals("UNZIP_ENTRY_INVALID", failure.getCode());
    }

    @Test
    void unzipKeepsDotDirectoryBoundary() throws IOException {
        final byte[] archiveBytes = zipBytes("docs/readme.txt", "content");
        final PutObjectRequest[] putRequest = new PutObjectRequest[1];
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("getObject".equals(methodName)) {
                    return completed(new ResponseInputStream<GetObjectResponse>(
                            GetObjectResponse.builder().build(),
                            new ByteArrayInputStream(archiveBytes)));
                }
                if ("putObject".equals(methodName)) {
                    putRequest[0] = (PutObjectRequest) args[0];
                    consumeRequestBody((AsyncRequestBody) args[1]);
                    return completed(PutObjectResponse.builder().build());
                }
                throw unsupported(methodName);
            }
        });

        UnzipResult result = operations(client).unzip(
                "bucket", "archive.zip", "bucket", "release.v1");

        assertEquals(1, result.getSucceeded().size());
        assertEquals("release.v1/docs/readme.txt", putRequest[0].key());
    }

    @Test
    void unzipRejectsArchivesWithTooManyEntries() throws IOException {
        final byte[] archiveBytes = zipDirectoryEntries(10001);
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("getObject".equals(methodName)) {
                    return completed(new ResponseInputStream<GetObjectResponse>(
                            GetObjectResponse.builder().build(),
                            new ByteArrayInputStream(archiveBytes)));
                }
                throw unsupported(methodName);
            }
        });

        OssException failure = assertThrows(OssException.class,
                () -> operations(client).unzip("bucket", "archive.zip", "bucket", "target"));

        assertEquals("UNZIP_LIMIT_EXCEEDED", failure.getCode());
    }

    @Test
    void unzipMarksEntryFailedWhenUploadFails() throws IOException {
        final byte[] archiveBytes = zipBytes("docs/readme.txt", "content");
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("getObject".equals(methodName)) {
                    return completed(new ResponseInputStream<GetObjectResponse>(
                            GetObjectResponse.builder().build(),
                            new ByteArrayInputStream(archiveBytes)));
                }
                if ("putObject".equals(methodName)) {
                    consumeRequestBody((AsyncRequestBody) args[1]);
                    return failed(S3Exception.builder().message("denied").build());
                }
                throw unsupported(methodName);
            }
        });

        UnzipResult result = operations(client).unzip("bucket", "archive.zip", "bucket", "target");

        assertEquals(0, result.getSucceeded().size());
        assertEquals(1, result.getFailed().size());
        assertEquals("docs/readme.txt", result.getFailed().get(0));
    }

    private static StreamUnzipOperations operations(S3AsyncClient client) {
        OssClientOptions options = new OssClientOptions("http://localhost:9000", "access-key", "secret-key", "minio", "bucket");
        return new StreamUnzipOperations(options, client, null);
    }

    private static byte[] zipBytes(String entryName, String content) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            zipOutputStream.putNextEntry(new ZipEntry(entryName));
            zipOutputStream.write(content.getBytes("UTF-8"));
            zipOutputStream.closeEntry();
        }
        return outputStream.toByteArray();
    }

    private static byte[] zipDirectoryEntries(int entryCount) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (int index = 0; index < entryCount; index++) {
                zipOutputStream.putNextEntry(new ZipEntry("directory-" + index + "/"));
                zipOutputStream.closeEntry();
            }
        }
        return outputStream.toByteArray();
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
        CompletableFuture<Object> future = new CompletableFuture<Object>();
        future.completeExceptionally(throwable);
        return future;
    }

    private static UnsupportedOperationException unsupported(String methodName) {
        return new UnsupportedOperationException(methodName);
    }

    private static void consumeRequestBody(AsyncRequestBody requestBody) {
        requestBody.subscribe(new Subscriber<ByteBuffer>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer byteBuffer) {
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private interface S3Handler {
        CompletableFuture<?> handle(String methodName, Object[] args);
    }
}
