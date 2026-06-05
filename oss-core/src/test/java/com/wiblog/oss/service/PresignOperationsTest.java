package com.wiblog.oss.service;

import com.wiblog.oss.config.OssClientOptions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PresignOperationsTest {

    @Test
    void getPresignedUrlKeepsExtensionlessObjectKey() {
        PresignOperations operations = operations();

        URI uri = URI.create(operations.generateGetPresignedUrl("bucket", "README", Duration.ofMinutes(5)));

        assertEquals("/bucket/README", uri.getPath());
        assertFalse(uri.getPath().endsWith("/README/"));
        operations.close();
    }

    @Test
    void putPresignedUrlKeepsExtensionlessObjectKey() {
        PresignOperations operations = operations();

        URI uri = URI.create(operations.generatePutPresignedUrl("bucket", "LICENSE",
                "text/plain", Duration.ofMinutes(5), null));

        assertEquals("/bucket/LICENSE", uri.getPath());
        assertFalse(uri.getPath().endsWith("/LICENSE/"));
        operations.close();
    }

    private static PresignOperations operations() {
        OssClientOptions options = new OssClientOptions("http://localhost:9000", "access-key", "secret-key", "minio", "bucket");
        return new PresignOperations(options, null, null);
    }
}
