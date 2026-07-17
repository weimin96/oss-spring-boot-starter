package com.wiblog.oss.service;

import com.wiblog.oss.bean.CopyObjectCommand;
import com.wiblog.oss.bean.PutObjectCommand;
import com.wiblog.oss.bean.StoredObject;
import com.wiblog.oss.bean.chunk.ChunkPartInfo;
import com.wiblog.oss.config.OssClientOptions;
import com.wiblog.oss.exception.OssException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.CopyPartResult;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.GetObjectTaggingResponse;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.ListPartsResponse;
import software.amazon.awssdk.services.s3.model.Part;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.UploadPartCopyRequest;
import software.amazon.awssdk.services.s3.model.UploadPartCopyResponse;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.FileUpload;
import software.amazon.awssdk.transfer.s3.model.CompletedUpload;
import software.amazon.awssdk.transfer.s3.model.Upload;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;
import software.amazon.awssdk.transfer.s3.model.UploadRequest;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PutOperationsTest {

    @Test
    void putObjectForKeyInputStreamMethodsRemainDefaultInterfaceAdapters() throws NoSuchMethodException {
        assertEquals(true, OssPutService.class
                .getMethod("putObjectForKey", String.class, InputStream.class)
                .isDefault());
        assertEquals(true, OssPutService.class
                .getMethod("putObjectForKey", String.class, String.class, InputStream.class)
                .isDefault());
        assertEquals(true, OssPutService.class
                .getMethod("copyFile", String.class, String.class)
                .isDefault());
        assertEquals(true, OssPutService.class
                .getMethod("copyFile", String.class, String.class, String.class, String.class)
                .isDefault());
    }

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
    void putObjectMapsCommandAndReturnsStorageResult() {
        final UploadRequest[] uploadRequest = new UploadRequest[1];
        final ByteArrayOutputStream uploadedBytes = new ByteArrayOutputStream();
        PutObjectResponse response = PutObjectResponse.builder()
                .eTag("etag-1")
                .versionId("version-1")
                .checksumSHA256("server-checksum")
                .build();
        S3TransferManager transferManager = transferManager(new TransferHandler() {
            @Override
            public Object handle(String methodName, Object[] args) {
                if ("upload".equals(methodName)) {
                    uploadRequest[0] = (UploadRequest) args[0];
                    return upload(consumeRequestBody(uploadRequest[0], uploadedBytes), response);
                }
                throw unsupported(methodName);
            }
        });
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("environment", "dev");

        StoredObject result = operations(s3Client(noS3Calls()), transferManager).putObject(new PutObjectCommand(
                "archive", "/docs/readme.txt", new ByteArrayInputStream(new byte[]{1, 2, 3}), 3L,
                "text/plain", Collections.singletonMap("owner", "team"), tags,
                "client-checksum", true));

        assertEquals("archive", uploadRequest[0].putObjectRequest().bucket());
        assertEquals("docs/readme.txt", uploadRequest[0].putObjectRequest().key());
        assertEquals(3L, uploadRequest[0].putObjectRequest().contentLength());
        assertEquals("text/plain", uploadRequest[0].putObjectRequest().contentType());
        assertEquals("team", uploadRequest[0].putObjectRequest().metadata().get("owner"));
        assertEquals("environment=dev", uploadRequest[0].putObjectRequest().tagging());
        assertEquals("client-checksum", uploadRequest[0].putObjectRequest().checksumSHA256());
        assertEquals("*", uploadRequest[0].putObjectRequest().ifNoneMatch());
        assertEquals(3, uploadedBytes.size());
        assertEquals(new StoredObject("archive", "docs/readme.txt", 3L,
                "etag-1", "version-1", "server-checksum"), result);
    }

    @Test
    void copyObjectReturnsHeadMetadataForDestination() {
        final CopyObjectRequest[] copyRequest = new CopyObjectRequest[1];
        List<HeadObjectRequest> headRequests = new ArrayList<>();
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("copyObject".equals(methodName)) {
                    copyRequest[0] = (CopyObjectRequest) args[0];
                    return completed(CopyObjectResponse.builder().build());
                }
                if ("headObject".equals(methodName)) {
                    headRequests.add((HeadObjectRequest) args[0]);
                    return completed(HeadObjectResponse.builder()
                            .contentLength(12L)
                            .eTag("etag-copy")
                            .versionId("version-copy")
                            .checksumSHA256("checksum-copy")
                            .build());
                }
                throw unsupported(methodName);
            }
        });

        StoredObject result = operations(client).copyObject(
                new CopyObjectCommand("source", "/a.txt", "destination", "/b.txt"));

        assertEquals("a.txt", copyRequest[0].sourceKey());
        assertEquals("version-copy", copyRequest[0].sourceVersionId());
        assertEquals("b.txt", copyRequest[0].destinationKey());
        assertEquals(2, headRequests.size());
        assertEquals("source", headRequests.get(0).bucket());
        assertEquals("a.txt", headRequests.get(0).key());
        assertNull(headRequests.get(0).checksumMode());
        assertEquals("destination", headRequests.get(1).bucket());
        assertEquals("b.txt", headRequests.get(1).key());
        assertEquals(ChecksumMode.ENABLED, headRequests.get(1).checksumMode());
        assertEquals(new StoredObject("destination", "b.txt", 12L,
                "etag-copy", "version-copy", "checksum-copy"), result);
    }

    @Test
    void copyFileKeepsExtensionlessObjectKeys() {
        final CopyObjectRequest[] copyRequest = new CopyObjectRequest[1];
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("headObject".equals(methodName)) {
                    return completed(HeadObjectResponse.builder()
                            .contentLength(1L)
                            .eTag("etag")
                            .build());
                }
                if ("copyObject".equals(methodName)) {
                    copyRequest[0] = (CopyObjectRequest) args[0];
                    return completed(CopyObjectResponse.builder().build());
                }
                throw unsupported(methodName);
            }
        });

        operations(client).copyFile("bucket", "bucket", "README", "LICENSE");

        assertEquals("README", copyRequest[0].sourceKey());
        assertEquals("etag", copyRequest[0].copySourceIfMatch());
        assertEquals("LICENSE", copyRequest[0].destinationKey());
    }

    @Test
    void copyObjectUsesMultipartCopyAboveSingleCopyThreshold() {
        long sourceSize = 5_000_000_001L;
        List<UploadPartCopyRequest> partRequests = new ArrayList<>();
        final CreateMultipartUploadRequest[] createRequest = new CreateMultipartUploadRequest[1];
        final GetObjectTaggingRequest[] taggingRequest = new GetObjectTaggingRequest[1];
        final CompleteMultipartUploadRequest[] completeRequest = new CompleteMultipartUploadRequest[1];
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("headObject".equals(methodName)) {
                    HeadObjectRequest request = (HeadObjectRequest) args[0];
                    if ("source.bin".equals(request.key())) {
                        return completed(HeadObjectResponse.builder()
                                .contentLength(sourceSize)
                                .eTag("source-etag")
                                .versionId("source-version")
                                .contentType("application/octet-stream")
                                .metadata(Collections.singletonMap("owner", "team"))
                                .build());
                    }
                    return completed(HeadObjectResponse.builder()
                            .contentLength(sourceSize)
                            .eTag("destination-etag")
                            .build());
                }
                if ("getObjectTagging".equals(methodName)) {
                    taggingRequest[0] = (GetObjectTaggingRequest) args[0];
                    return completed(GetObjectTaggingResponse.builder()
                            .tagSet(Tag.builder().key("environment").value("dev").build())
                            .build());
                }
                if ("createMultipartUpload".equals(methodName)) {
                    createRequest[0] = (CreateMultipartUploadRequest) args[0];
                    return completed(CreateMultipartUploadResponse.builder().uploadId("upload-id").build());
                }
                if ("uploadPartCopy".equals(methodName)) {
                    UploadPartCopyRequest request = (UploadPartCopyRequest) args[0];
                    partRequests.add(request);
                    return completed(UploadPartCopyResponse.builder()
                            .copyPartResult(CopyPartResult.builder()
                                    .eTag("part-" + request.partNumber())
                                    .build())
                            .build());
                }
                if ("completeMultipartUpload".equals(methodName)) {
                    completeRequest[0] = (CompleteMultipartUploadRequest) args[0];
                    return completed(CompleteMultipartUploadResponse.builder().build());
                }
                throw unsupported(methodName);
            }
        });

        StoredObject result = operations(client).copyObject(
                new CopyObjectCommand("source", "/source.bin", "destination", "/target.bin"));

        assertEquals("source-version", taggingRequest[0].versionId());
        assertEquals("application/octet-stream", createRequest[0].contentType());
        assertEquals("team", createRequest[0].metadata().get("owner"));
        assertEquals("environment=dev", createRequest[0].tagging());
        assertEquals(75, partRequests.size());
        assertEquals("bytes=0-67108863", partRequests.get(0).copySourceRange());
        assertEquals("source-version", partRequests.get(0).sourceVersionId());
        assertEquals("bytes=4966055936-5000000000",
                partRequests.get(partRequests.size() - 1).copySourceRange());
        assertEquals(75, completeRequest[0].multipartUpload().parts().size());
        assertEquals(new StoredObject("destination", "target.bin", sourceSize,
                "destination-etag", null, null), result);
    }

    @Test
    void copyObjectAbortsMultipartUploadWhenPartCopyFails() {
        long sourceSize = 5_000_000_001L;
        final AbortMultipartUploadRequest[] abortRequest = new AbortMultipartUploadRequest[1];
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("headObject".equals(methodName)) {
                    return completed(HeadObjectResponse.builder()
                            .contentLength(sourceSize)
                            .eTag("source-etag")
                            .build());
                }
                if ("getObjectTagging".equals(methodName)) {
                    return completed(GetObjectTaggingResponse.builder().build());
                }
                if ("createMultipartUpload".equals(methodName)) {
                    return completed(CreateMultipartUploadResponse.builder().uploadId("upload-id").build());
                }
                if ("uploadPartCopy".equals(methodName)) {
                    return failed(S3Exception.builder().message("part failed").build());
                }
                if ("abortMultipartUpload".equals(methodName)) {
                    abortRequest[0] = (AbortMultipartUploadRequest) args[0];
                    return completed(AbortMultipartUploadResponse.builder().build());
                }
                throw unsupported(methodName);
            }
        });

        OssException failure = assertThrows(OssException.class, () -> operations(client).copyObject(
                new CopyObjectCommand("source", "source.bin", "destination", "target.bin")));

        assertEquals("OBJECT_MULTIPART_COPY_PART_FAILED", failure.getCode());
        assertEquals("upload-id", abortRequest[0].uploadId());
        assertEquals("target.bin", abortRequest[0].key());
    }

    @Test
    void copyObjectRejectsObjectLargerThanMultipartLimitBeforeInit() {
        long sourceSize = 5L * 1024 * 1024 * 1024 * 10000 + 1;
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("headObject".equals(methodName)) {
                    return completed(HeadObjectResponse.builder()
                            .contentLength(sourceSize)
                            .eTag("source-etag")
                            .build());
                }
                throw unsupported(methodName);
            }
        });

        OssException failure = assertThrows(OssException.class, () -> operations(client).copyObject(
                new CopyObjectCommand("source", "source.bin", "destination", "target.bin")));

        assertEquals("OBJECT_COPY_TOO_LARGE", failure.getCode());
    }

    @Test
    void copyObjectRejectsSourceWithoutStableIdentity() {
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("headObject".equals(methodName)) {
                    return completed(HeadObjectResponse.builder().contentLength(1L).build());
                }
                throw unsupported(methodName);
            }
        });

        OssException failure = assertThrows(OssException.class, () -> operations(client).copyObject(
                new CopyObjectCommand("source", "source.bin", "destination", "target.bin")));

        assertEquals("OBJECT_COPY_SOURCE_IDENTITY_MISSING", failure.getCode());
    }

    @Test
    void copyObjectRejectsSameSourceAndDestination() {
        PutOperations operations = operations(s3Client(noS3Calls()));

        assertThrows(IllegalArgumentException.class, () -> operations.copyObject(
                new CopyObjectCommand("bucket", "/same.txt", "bucket", "same.txt")));
    }

    @Test
    void moveKeepsExtensionlessSourceKeyWhenDeleting() {
        List<CopyObjectRequest> copyRequests = new ArrayList<>();
        List<HeadObjectRequest> headRequests = new ArrayList<>();
        List<DeleteObjectRequest> deleteRequests = new ArrayList<>();
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("copyObject".equals(methodName)) {
                    copyRequests.add((CopyObjectRequest) args[0]);
                    return completed(CopyObjectResponse.builder().build());
                }
                if ("headObject".equals(methodName)) {
                    headRequests.add((HeadObjectRequest) args[0]);
                    return completed(headResponse(6L, "checksum"));
                }
                if ("deleteObject".equals(methodName)) {
                    deleteRequests.add(buildDeleteObjectRequest((Consumer<?>) args[0]));
                    return completed(DeleteObjectResponse.builder().build());
                }
                throw unsupported(methodName);
            }
        });

        operations(client).move("bucket", "README", "archive");

        assertEquals(2, copyRequests.size());
        assertEquals("README", copyRequests.get(0).sourceKey());
        assertEquals(true, copyRequests.get(0).destinationKey().startsWith(".oss-staging/move/"));
        assertEquals(copyRequests.get(0).destinationKey(), copyRequests.get(1).sourceKey());
        assertEquals("archive/README", copyRequests.get(1).destinationKey());
        assertEquals(3, headRequests.size());
        assertEquals(ChecksumMode.ENABLED, headRequests.get(0).checksumMode());
        assertEquals(2, deleteRequests.size());
        assertEquals(copyRequests.get(0).destinationKey(), deleteRequests.get(0).key());
        assertEquals("README", deleteRequests.get(1).key());
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
                if ("headObject".equals(methodName)) {
                    return completed(HeadObjectResponse.builder()
                            .contentLength(1L)
                            .eTag("etag")
                            .build());
                }
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
        List<DeleteObjectRequest> deleteRequests = new ArrayList<>();
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("headObject".equals(methodName)) {
                    return completed(headResponse(6L, null));
                }
                if ("copyObject".equals(methodName)) {
                    return failed(S3Exception.builder().message("missing source").build());
                }
                if ("deleteObject".equals(methodName)) {
                    deleteRequests.add(buildDeleteObjectRequest((Consumer<?>) args[0]));
                    return completed(DeleteObjectResponse.builder().build());
                }
                throw unsupported(methodName);
            }
        });

        assertThrows(OssException.class, () -> operations(client).move("bucket", "source.txt", "archive"));
        assertEquals(1, deleteRequests.size());
        assertEquals(true, deleteRequests.get(0).key().startsWith(".oss-staging/move/"));
    }

    @Test
    void moveDoesNotCopyToDestinationWhenStagingChecksumDiffers() {
        List<CopyObjectRequest> copyRequests = new ArrayList<>();
        List<DeleteObjectRequest> deleteRequests = new ArrayList<>();
        final int[] headCount = new int[]{0};
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("headObject".equals(methodName)) {
                    headCount[0]++;
                    return completed(headResponse(6L,
                            headCount[0] == 1 ? "source-checksum" : "staging-checksum"));
                }
                if ("copyObject".equals(methodName)) {
                    copyRequests.add((CopyObjectRequest) args[0]);
                    return completed(CopyObjectResponse.builder().build());
                }
                if ("deleteObject".equals(methodName)) {
                    deleteRequests.add(buildDeleteObjectRequest((Consumer<?>) args[0]));
                    return completed(DeleteObjectResponse.builder().build());
                }
                throw unsupported(methodName);
            }
        });

        OssException failure = assertThrows(OssException.class,
                () -> operations(client).move("bucket", "source.txt", "archive"));

        assertEquals("MOVE_STAGING_INVALID", failure.getCode());
        assertEquals(1, copyRequests.size());
        assertEquals(1, deleteRequests.size());
        assertEquals(copyRequests.get(0).destinationKey(), deleteRequests.get(0).key());
    }

    @Test
    void moveKeepsSourceAndCleansStagingWhenDestinationCopyFails() {
        List<CopyObjectRequest> copyRequests = new ArrayList<>();
        List<DeleteObjectRequest> deleteRequests = new ArrayList<>();
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("headObject".equals(methodName)) {
                    return completed(headResponse(6L, null));
                }
                if ("copyObject".equals(methodName)) {
                    copyRequests.add((CopyObjectRequest) args[0]);
                    return copyRequests.size() == 1
                            ? completed(CopyObjectResponse.builder().build())
                            : failed(S3Exception.builder().message("destination unavailable").build());
                }
                if ("deleteObject".equals(methodName)) {
                    deleteRequests.add(buildDeleteObjectRequest((Consumer<?>) args[0]));
                    return completed(DeleteObjectResponse.builder().build());
                }
                throw unsupported(methodName);
            }
        });

        assertThrows(OssException.class, () -> operations(client).move("bucket", "source.txt", "archive"));

        assertEquals(2, copyRequests.size());
        assertEquals(1, deleteRequests.size());
        assertEquals(copyRequests.get(0).destinationKey(), deleteRequests.get(0).key());
    }

    @Test
    void moveKeepsSourceWhenDestinationValidationFails() {
        List<DeleteObjectRequest> deleteRequests = new ArrayList<>();
        final int[] headCount = new int[]{0};
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("headObject".equals(methodName)) {
                    headCount[0]++;
                    return completed(headResponse(headCount[0] == 3 ? 5L : 6L, null));
                }
                if ("copyObject".equals(methodName)) {
                    return completed(CopyObjectResponse.builder().build());
                }
                if ("deleteObject".equals(methodName)) {
                    deleteRequests.add(buildDeleteObjectRequest((Consumer<?>) args[0]));
                    return completed(DeleteObjectResponse.builder().build());
                }
                throw unsupported(methodName);
            }
        });

        OssException failure = assertThrows(OssException.class,
                () -> operations(client).move("bucket", "source.txt", "archive"));

        assertEquals("MOVE_DESTINATION_INVALID", failure.getCode());
        assertEquals(1, deleteRequests.size());
        assertEquals(true, deleteRequests.get(0).key().startsWith(".oss-staging/move/"));
    }

    @Test
    void moveDoesNotDeleteSourceWhenStagingCleanupFails() {
        List<DeleteObjectRequest> deleteRequests = new ArrayList<>();
        S3AsyncClient client = successfulMoveClient(deleteRequests, true, false);

        OssException failure = assertThrows(OssException.class,
                () -> operations(client).move("bucket", "source.txt", "archive"));

        assertEquals("MOVE_STAGING_DELETE_FAILED", failure.getCode());
        assertEquals(2, deleteRequests.size());
        assertEquals(true, deleteRequests.get(0).key().startsWith(".oss-staging/move/"));
        assertEquals(deleteRequests.get(0).key(), deleteRequests.get(1).key());
        assertEquals(1, failure.getSuppressed().length);
    }

    @Test
    void moveReportsSourceDeleteFailureAfterStagingWasRemoved() {
        List<DeleteObjectRequest> deleteRequests = new ArrayList<>();
        S3AsyncClient client = successfulMoveClient(deleteRequests, false, true);

        OssException failure = assertThrows(OssException.class,
                () -> operations(client).move("bucket", "source.txt", "archive"));

        assertEquals("MOVE_SOURCE_DELETE_FAILED", failure.getCode());
        assertEquals(2, deleteRequests.size());
        assertEquals(true, deleteRequests.get(0).key().startsWith(".oss-staging/move/"));
        assertEquals("source.txt", deleteRequests.get(1).key());
    }

    @Test
    void moveToCurrentDirectoryIsIdempotent() {
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("headObject".equals(methodName)) {
                    return completed(headResponse(6L, null));
                }
                throw unsupported(methodName);
            }
        });

        operations(client).move("bucket", "archive/source.txt", "archive");
    }

    @Test
    void moveToCurrentDirectoryStillRequiresSourceToExist() {
        S3AsyncClient client = s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("headObject".equals(methodName)) {
                    return failed(S3Exception.builder().message("missing source").build());
                }
                throw unsupported(methodName);
            }
        });

        OssException failure = assertThrows(OssException.class,
                () -> operations(client).move("bucket", "archive/source.txt", "archive"));

        assertEquals("MOVE_SOURCE_HEAD_FAILED", failure.getCode());
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

    private static S3AsyncClient successfulMoveClient(List<DeleteObjectRequest> deleteRequests,
                                                      boolean failStagingDelete, boolean failSourceDelete) {
        return s3Client(new S3Handler() {
            @Override
            public CompletableFuture<?> handle(String methodName, Object[] args) {
                if ("headObject".equals(methodName)) {
                    return completed(headResponse(6L, "checksum"));
                }
                if ("copyObject".equals(methodName)) {
                    return completed(CopyObjectResponse.builder().build());
                }
                if ("deleteObject".equals(methodName)) {
                    DeleteObjectRequest request = buildDeleteObjectRequest((Consumer<?>) args[0]);
                    deleteRequests.add(request);
                    boolean stagingDelete = request.key().startsWith(".oss-staging/move/");
                    if ((stagingDelete && failStagingDelete) || (!stagingDelete && failSourceDelete)) {
                        return failed(S3Exception.builder().message("delete failed").build());
                    }
                    return completed(DeleteObjectResponse.builder().build());
                }
                throw unsupported(methodName);
            }
        });
    }

    private static HeadObjectResponse headResponse(long size, String checksum) {
        return HeadObjectResponse.builder()
                .contentLength(size)
                .checksumSHA256(checksum)
                .build();
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
        return upload(consumed, PutObjectResponse.builder().build());
    }

    private static Upload upload(CompletableFuture<Void> consumed, PutObjectResponse response) {
        return (Upload) Proxy.newProxyInstance(
                Upload.class.getClassLoader(),
                new Class<?>[]{Upload.class},
                (proxy, method, args) -> {
                    if ("completionFuture".equals(method.getName())) {
                        return consumed.thenApply(ignored -> CompletedUpload.builder().response(response).build());
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
