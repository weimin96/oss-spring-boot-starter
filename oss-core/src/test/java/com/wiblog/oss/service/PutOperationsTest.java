package com.wiblog.oss.service;

import com.wiblog.oss.bean.chunk.ChunkPartInfo;
import com.wiblog.oss.config.OssClientOptions;
import com.wiblog.oss.exception.OssException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.ListPartsResponse;
import software.amazon.awssdk.services.s3.model.Part;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PutOperationsTest {

    @Test
    void listPartsUsesS3MaxPageSizeAndReadsAllPages() {
        List<ListPartsRequest> requests = new ArrayList<>();
        S3AsyncClient client = s3Client(request -> {
            requests.add(request);
            if (requests.size() == 1) {
                return ListPartsResponse.builder()
                        .isTruncated(true)
                        .nextPartNumberMarker(1000)
                        .parts(Part.builder().partNumber(1).eTag("etag-1").size(1024L).build())
                        .build();
            }
            return ListPartsResponse.builder()
                    .isTruncated(false)
                    .parts(Part.builder().partNumber(1001).eTag("etag-1001").size(2048L).build())
                    .build();
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
        S3AsyncClient client = s3Client(request -> ListPartsResponse.builder()
                .isTruncated(true)
                .parts(Part.builder().partNumber(1).eTag("etag-1").size(1024L).build())
                .build());

        assertThrows(OssException.class, () -> operations(client).listParts("bucket", "object.txt", "upload-id"));
    }

    private static PutOperations operations(S3AsyncClient client) {
        OssClientOptions options = new OssClientOptions("http://localhost:9000", "access-key", "secret-key", "minio", "bucket");
        return new PutOperations(options, client, null);
    }

    private static S3AsyncClient s3Client(ListPartsHandler handler) {
        return (S3AsyncClient) Proxy.newProxyInstance(
                S3AsyncClient.class.getClassLoader(),
                new Class<?>[]{S3AsyncClient.class},
                (proxy, method, args) -> {
                    if ("listParts".equals(method.getName())) {
                        return CompletableFuture.completedFuture(handler.handle((ListPartsRequest) args[0]));
                    }
                    if ("serviceName".equals(method.getName())) {
                        return "s3";
                    }
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private interface ListPartsHandler {
        ListPartsResponse handle(ListPartsRequest request);
    }
}
