package com.wiblog.oss.service;

import com.wiblog.oss.config.OssClientOptions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OssTemplateTest {

    @Test
    void standardAsyncClientUsesApiTimeoutsAndRequiredChecksums() {
        OssClientOptions options = options();
        options.setApiCallTimeout(300_000L);
        options.setApiCallAttemptTimeout(60_000L);

        S3AsyncClient client = OssTemplate.buildClient(options);
        try {
            assertEquals(Duration.ofMillis(300_000L),
                    client.serviceClientConfiguration().overrideConfiguration().apiCallTimeout().get());
            assertEquals(Duration.ofMillis(60_000L),
                    client.serviceClientConfiguration().overrideConfiguration().apiCallAttemptTimeout().get());
            assertEquals(RequestChecksumCalculation.WHEN_REQUIRED,
                    client.serviceClientConfiguration().requestChecksumCalculation());
            assertEquals(ResponseChecksumValidation.WHEN_REQUIRED,
                    client.serviceClientConfiguration().responseChecksumValidation());
        } finally {
            client.close();
        }
    }

    @Test
    void singlePartClientDoesNotUseMultipartWrapper() {
        S3AsyncClient client = OssTemplate.buildSinglePartClient(options());
        try {
            assertFalse(client.getClass().getName().contains("MultipartS3AsyncClient"));
        } finally {
            client.close();
        }
    }

    @Test
    void invalidAttemptTimeoutIsRejectedBeforeClientCreation() {
        OssClientOptions options = options();
        options.setApiCallTimeout(1_000L);
        options.setApiCallAttemptTimeout(2_000L);

        assertThrows(IllegalArgumentException.class, () -> OssTemplate.buildClient(options));
    }

    @Test
    void partSizeAboveS3LimitIsRejectedBeforeClientCreation() {
        OssClientOptions options = options();
        options.setPartSizeInMb(5121);

        assertThrows(IllegalArgumentException.class, () -> OssTemplate.buildClient(options));
    }

    @Test
    void closeAllContinuesAfterFailureAndPreservesEveryError() {
        List<String> closedResources = new ArrayList<>();
        AutoCloseable first = closeable("presigner", closedResources, new IllegalStateException("first"));
        AutoCloseable second = closeable("transfer-manager", closedResources, null);
        AutoCloseable third = closeable("s3-client", closedResources, new IllegalArgumentException("third"));

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> OssTemplate.closeAll(first, second, third));

        assertEquals(Arrays.asList("presigner", "transfer-manager", "s3-client"), closedResources);
        assertEquals("first", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("third", failure.getSuppressed()[0].getMessage());
    }

    private static OssClientOptions options() {
        return new OssClientOptions("http://localhost:9000", "access-key", "secret-key", "minio");
    }

    private static AutoCloseable closeable(String name, List<String> closedResources,
                                           RuntimeException failure) {
        return () -> {
            closedResources.add(name);
            if (failure != null) {
                throw failure;
            }
        };
    }
}
