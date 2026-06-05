package com.wiblog.oss.service;

import com.wiblog.oss.bean.chunk.ChunkPartInfo;
import com.wiblog.oss.config.OssClientOptions;
import com.wiblog.oss.exception.OssException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.ListPartsResponse;
import software.amazon.awssdk.services.s3.model.Part;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PutOperationsTest {

    @Test
    void createBucketFailsWhenCreateRequestFails() {
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("headBucket".equals(methodName) || "createBucket".equals(methodName)) {
                    return failed(S3Exception.builder().message("denied").build());
                }
                throw unsupported(methodName);
            }
        });

        assertThrows(OssException.class, () -> operations(client).createBucket("bucket"));
    }

    @Test
    void mkdirsFailsWhenPutObjectFails() {
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("putObject".equals(methodName)) {
                    return failed(S3Exception.builder().message("denied").build());
                }
                throw unsupported(methodName);
            }
        });

        assertThrows(OssException.class, () -> operations(client).mkdirs("bucket", "docs"));
    }

    @Test
    void copyFileFailsWhenCopyRequestFails() {
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("copyObject".equals(methodName)) {
                    return failed(S3Exception.builder().message("missing source").build());
                }
                throw unsupported(methodName);
            }
        });

        assertThrows(OssException.class, () -> operations(client).copyFile("bucket", "bucket", "source.txt", "target.txt"));
    }

    @Test
    void moveDoesNotDeleteSourceWhenCopyFails() {
        final boolean[] deleteRequested = new boolean[]{false};
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("copyObject".equals(methodName)) {
                    return failed(S3Exception.builder().message("missing source").build());
                }
                if ("deleteObject".equals(methodName)) {
                    deleteRequested[0] = true;
                    return completed(DeleteObjectResponse.builder().build());
                }
                throw unsupported(methodName);
            }
        });

        assertThrows(OssException.class, () -> operations(client).move("bucket", "source.txt", "archive"));
        assertEquals(false, deleteRequested[0]);
    }

    @Test
    void listPartsUsesS3MaxPageSizeAndReadsAllPages() {
        List<ListPartsRequest> requests = new ArrayList<>();
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("listParts".equals(methodName)) {
                    ListPartsRequest request = (ListPartsRequest) args[0];
                    requests.add(request);
                    if (requests.size() == 1) {
                        return completed(ListPartsResponse.builder()
                                .isTruncated(true)
                                .nextPartNumberMarker(1000)
                                .parts(Part.builder().partNumber(1).eTag("etag-1").size(1024L).build())
                                .build());
                    }
                    return completed(ListPartsResponse.builder()
                            .isTruncated(false)
                            .parts(Part.builder().partNumber(1001).eTag("etag-1001").size(2048L).build())
                            .build());
                }
                throw unsupported(methodName);
            }
        });

        List<ChunkPartInfo> parts = operations(client).listParts("bucket", "object.txt", "upload-id");

        assertEquals(2, requests.size());
        assertEquals(1000, requests.get(0).maxParts());
        assertNull(requests.get(0).partNumberMarker());
        assertEquals(1000, requests.get(1).maxParts());
        assertEquals(1000, requests.get(1).partNumberMarker());
        assertEquals(2, parts.size());
        assertEquals(1, parts.get(0).getPartNumber());
        assertEquals("etag-1", parts.get(0).getEtag());
        assertEquals(1024L, parts.get(0).getSize());
        assertEquals(1001, parts.get(1).getPartNumber());
    }

    @Test
    void listPartsFailsWhenTruncatedResponseMissingNextMarker() {
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("listParts".equals(methodName)) {
                    return completed(ListPartsResponse.builder()
                            .isTruncated(true)
                            .parts(Part.builder().partNumber(1).eTag("etag-1").size(1024L).build())
                            .build());
                }
                throw unsupported(methodName);
            }
        });

        assertThrows(OssException.class, () -> operations(client).listParts("bucket", "object.txt", "upload-id"));
    }

    private static PutOperations operations(S3AsyncClient client) {
        OssClientOptions options = new OssClientOptions("http://localhost:9000", "access-key", "secret-key", "minio", "bucket");
        return new PutOperations(options, client, null);
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
