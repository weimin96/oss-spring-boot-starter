package com.wiblog.oss.service;

import com.wiblog.oss.config.OssClientOptions;
import com.wiblog.oss.exception.OssException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeleteOperationsTest {

    @Test
    void removeFolderRejectsEmptyPrefixBeforeListingBucket() {
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                throw unsupported(methodName);
            }
        });

        assertThrows(IllegalArgumentException.class,
                () -> operations(client).removeFolder("bucket", ""));
    }

    @Test
    void removeObjectDoesNotReportPermissionFailureAsMissing() {
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("headObject".equals(methodName)) {
                    return failed(S3Exception.builder()
                            .statusCode(403)
                            .awsErrorDetails(AwsErrorDetails.builder()
                                    .errorCode("AccessDenied")
                                    .build())
                            .message("denied")
                            .build());
                }
                throw unsupported(methodName);
            }
        });

        OssException failure = assertThrows(OssException.class,
                () -> operations(client).removeObject("bucket", "object.txt"));

        assertEquals("OBJECT_DELETE_FORBIDDEN", failure.getCode());
    }

    @Test
    void removeObjectFailsWhenDeleteRequestFails() {
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("headObject".equals(methodName)) {
                    return completed(HeadObjectResponse.builder()
                            .lastModified(Instant.parse("2026-06-05T00:00:00Z"))
                            .contentLength(1L)
                            .build());
                }
                if ("deleteObject".equals(methodName)) {
                    return failed(S3Exception.builder().message("denied").build());
                }
                throw unsupported(methodName);
            }
        });

        assertThrows(OssException.class, () -> operations(client).removeObject("bucket", "object.txt"));
    }

    private static DeleteOperations operations(S3AsyncClient client) {
        OssClientOptions options = new OssClientOptions("http://localhost:9000", "access-key", "secret-key", "minio", "bucket");
        return new DeleteOperations(options, client, null);
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
}
