package com.wiblog.oss.service;

import com.wiblog.oss.bean.chunk.ChunkPartInfo;
import com.wiblog.oss.config.OssClientOptions;
import com.wiblog.oss.exception.OssException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.ListPartsResponse;
import software.amazon.awssdk.services.s3.model.Part;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.FileUpload;
import software.amazon.awssdk.transfer.s3.model.Upload;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;
import software.amazon.awssdk.transfer.s3.model.UploadRequest;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.File;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PutOperationsTest {

    @Test
    void putObjectForKeyKeepsExtensionlessObjectKey(@TempDir Path tempDir) throws IOException {
        File sourceFile = tempDir.resolve("README").toFile();
        Files.write(sourceFile.toPath(), new byte[]{1});
        final UploadFileRequest[] uploadRequest = new UploadFileRequest[1];
        S3TransferManager transferManager = transferManager(new TransferHandler() {
            @Override
            public Object handle(String methodName, Object[] args) {
                if ("uploadFile".equals(methodName)) {
                    uploadRequest[0] = (UploadFileRequest) args[0];
                    return fileUpload();
                }
                throw unsupported(methodName);
            }
        });

        operations(s3Client(noS3Calls()), transferManager)
                .putObjectForKey("bucket", "README", sourceFile);

        assertEquals("README", uploadRequest[0].putObjectRequest().key());
    }

    @Test
    void putObjectForKeyUploadsInputStreamWithoutBufferingWholeStream() {
        final UploadRequest[] uploadRequest = new UploadRequest[1];
        final ByteArrayOutputStream uploadedBytes = new ByteArrayOutputStream();
        S3TransferManager transferManager = transferManager(new TransferHandler() {
            @Override
            public Object handle(String methodName, Object[] args) {
                if ("upload".equals(methodName)) {
                    uploadRequest[0] = (UploadRequest) args[0];
                    CompletableFuture<Void> consumed = consumeRequestBody(uploadRequest[0], uploadedBytes);
                    return upload(consumed);
                }
                throw unsupported(methodName);
            }
        });

        operations(s3Client(noS3Calls()), transferManager)
                .putObjectForKey("bucket", "large-object", new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));

        assertEquals("large-object", uploadRequest[0].putObjectRequest().key());
        assertEquals(false, uploadRequest[0].requestBody().contentLength().isPresent());
        assertEquals(4, uploadedBytes.size());
    }

    @Test
    void copyFileKeepsExtensionlessObjectKeys() {
        final CopyObjectRequest[] copyRequest = new CopyObjectRequest[1];
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("copyObject".equals(methodName)) {
                    copyRequest[0] = (CopyObjectRequest) args[0];
                    return completed(CopyObjectResponse.builder().build());
                }
                throw unsupported(methodName);
            }
        });

        operations(client).copyFile("bucket", "bucket", "README", "LICENSE");

        assertEquals("README", copyRequest[0].sourceKey());
        assertEquals("LICENSE", copyRequest[0].destinationKey());
    }

    @Test
    void moveKeepsExtensionlessSourceKeyWhenDeleting() {
        final CopyObjectRequest[] copyRequest = new CopyObjectRequest[1];
        final DeleteObjectRequest[] deleteRequest = new DeleteObjectRequest[1];
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("copyObject".equals(methodName)) {
                    copyRequest[0] = (CopyObjectRequest) args[0];
                    return completed(CopyObjectResponse.builder().build());
                }
                if ("deleteObject".equals(methodName)) {
                    deleteRequest[0] = buildDeleteObjectRequest((Consumer<?>) args[0]);
                    return completed(DeleteObjectResponse.builder().build());
                }
                throw unsupported(methodName);
            }
        });

        operations(client).move("bucket", "README", "archive");

        assertEquals("README", copyRequest[0].sourceKey());
        assertEquals("archive/README", copyRequest[0].destinationKey());
        assertEquals("README", deleteRequest[0].key());
    }

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

    private static PutOperations operations(S3AsyncClient client, S3TransferManager transferManager) {
        OssClientOptions options = new OssClientOptions("http://localhost:9000", "access-key", "secret-key", "minio", "bucket");
        return new PutOperations(options, client, transferManager);
    }

    private static S3Handler noS3Calls() {
        return new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                throw unsupported(methodName);
            }
        };
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

    private static S3TransferManager transferManager(TransferHandler handler) {
        return (S3TransferManager) Proxy.newProxyInstance(
                S3TransferManager.class.getClassLoader(),
                new Class<?>[]{S3TransferManager.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("close".equals(methodName)) {
                        return null;
                    }
                    return handler.handle(methodName, args);
                });
    }

    private static FileUpload fileUpload() {
        return (FileUpload) Proxy.newProxyInstance(
                FileUpload.class.getClassLoader(),
                new Class<?>[]{FileUpload.class},
                (proxy, method, args) -> {
                    if ("completionFuture".equals(method.getName())) {
                        return CompletableFuture.completedFuture(null);
                    }
                    throw unsupported(method.getName());
                });
    }

    private static Upload upload(CompletableFuture<Void> consumed) {
        return (Upload) Proxy.newProxyInstance(
                Upload.class.getClassLoader(),
                new Class<?>[]{Upload.class},
                (proxy, method, args) -> {
                    if ("completionFuture".equals(method.getName())) {
                        return consumed.thenApply(ignored -> null);
                    }
                    throw unsupported(method.getName());
                });
    }

    private static CompletableFuture<Void> consumeRequestBody(UploadRequest request, ByteArrayOutputStream outputStream) {
        CompletableFuture<Void> consumed = new CompletableFuture<>();
        request.requestBody().subscribe(new Subscriber<ByteBuffer>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer byteBuffer) {
                byte[] bytes = new byte[byteBuffer.remaining()];
                byteBuffer.get(bytes);
                outputStream.write(bytes, 0, bytes.length);
            }

            @Override
            public void onError(Throwable throwable) {
                consumed.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                consumed.complete(null);
            }
        });
        return consumed;
    }

    @SuppressWarnings("unchecked")
    private static DeleteObjectRequest buildDeleteObjectRequest(Consumer<?> requestConsumer) {
        DeleteObjectRequest.Builder builder = DeleteObjectRequest.builder();
        ((Consumer<DeleteObjectRequest.Builder>) requestConsumer).accept(builder);
        return builder.build();
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

    private interface TransferHandler {
        Object handle(String methodName, Object[] args);
    }
}
