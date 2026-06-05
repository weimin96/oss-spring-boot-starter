package com.wiblog.oss.service;

import com.wiblog.oss.config.OssClientOptions;
import com.wiblog.oss.exception.OssException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;

class BucketOperationsTest {

    @Test
    void putBucketPolicyFailsWhenRequestFails() {
        assertWriteFailure("putBucketPolicy",
                operations -> operations.putBucketPolicy("bucket", "{}"));
    }

    @Test
    void deleteBucketPolicyFailsWhenRequestFails() {
        assertWriteFailure("deleteBucketPolicy",
                operations -> operations.deleteBucketPolicy("bucket"));
    }

    @Test
    void blockAllPublicAccessFailsWhenRequestFails() {
        assertWriteFailure("putPublicAccessBlock",
                operations -> operations.blockAllPublicAccess("bucket"));
    }

    @Test
    void enableServerSideEncryptionFailsWhenRequestFails() {
        assertWriteFailure("putBucketEncryption",
                operations -> operations.enableServerSideEncryption("bucket"));
    }

    @Test
    void enableVersioningFailsWhenRequestFails() {
        assertWriteFailure("putBucketVersioning",
                operations -> operations.enableVersioning("bucket"));
    }

    private static void assertWriteFailure(String failedMethodName, BucketWriteAction action) {
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if (failedMethodName.equals(methodName)) {
                    return failed(S3Exception.builder().message("denied").build());
                }
                throw unsupported(methodName);
            }
        });

        assertThrows(OssException.class, () -> action.execute(operations(client)));
    }

    private static BucketOperations operations(S3AsyncClient client) {
        OssClientOptions options = new OssClientOptions("http://localhost:9000", "access-key", "secret-key", "minio", "bucket");
        return new BucketOperations(options, client, null);
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

    private static CompletableFuture<Object> failed(Throwable throwable) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }

    private static UnsupportedOperationException unsupported(String methodName) {
        return new UnsupportedOperationException(methodName);
    }

    private interface BucketWriteAction {
        void execute(BucketOperations operations);
    }

    private interface S3Handler {
        CompletableFuture<?> handle(String methodName, Object[] args);
    }
}
