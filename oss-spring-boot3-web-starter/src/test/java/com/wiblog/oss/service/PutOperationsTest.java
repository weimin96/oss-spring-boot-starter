package com.wiblog.oss.service;

import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.CopyObjectCommand;
import com.wiblog.oss.bean.PutObjectCommand;
import com.wiblog.oss.bean.StoredObject;
import com.wiblog.oss.bean.chunk.ChunkMerge;
import com.wiblog.oss.bean.chunk.ChunkTarget;
import com.wiblog.oss.bean.chunk.ChunkTask;
import com.wiblog.oss.bean.chunk.ChunkUploadCommand;
import com.wiblog.oss.config.OssClientOptions;
import com.wiblog.oss.support.AbstractServiceDynamicPropertyTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PutOperations 集成测试。
 */
@DisplayName("PutOperations")
class PutOperationsTest extends AbstractServiceDynamicPropertyTest {

    @Test
    @DisplayName("InputStream 上传后应能查询到对象内容")
    void putObjectFromInputStream() {
        String directory = newTestDirectory();
        ByteArrayInputStream stream = new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8));

        ObjectInfo info = ossTemplate.put().putObject(directory, "hello.txt", stream);

        assertThat(info.getUri()).isEqualTo(directory + "/hello.txt");
        assertThat(ossTemplate.query().getContent(directory + "/hello.txt")).isEqualTo("hello");
    }

    @Test
    @DisplayName("available 返回 0 的流也应被完整上传")
    void putObjectWhenAvailableReturnsZero() {
        String directory = newTestDirectory();
        InputStream stream = new InputStream() {
            private final byte[] data = "content".getBytes(StandardCharsets.UTF_8);
            private int index = 0;

            @Override
            public int read() {
                return index < data.length ? data[index++] : -1;
            }

            @Override
            public int available() {
                return 0;
            }
        };

        ObjectInfo info = ossTemplate.put().putObject(directory, "available-zero.txt", stream);

        assertThat(info.getSize()).isEqualTo("content".length());
        assertThat(ossTemplate.query().getContent(directory + "/available-zero.txt")).isEqualTo("content");
    }

    @Test
    @DisplayName("对象命令应保存 metadata、tags 与 SHA-256 checksum")
    void putObjectWithMetadataTagsAndChecksum() throws Exception {
        String key = newTestDirectory() + "/command.txt";
        byte[] content = "command-content".getBytes(StandardCharsets.UTF_8);
        String checksum = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(content));

        StoredObject uploaded = ossTemplate.put().putObject(new PutObjectCommand(
                null,
                key,
                new ByteArrayInputStream(content),
                (long) content.length,
                "text/plain",
                Map.of("source", "integration"),
                Map.of("stage", "verified"),
                checksum,
                false
        ));

        StoredObject headed = ossTemplate.query().headObject(ossProperties.getBucketName(), key);
        try (S3AsyncClient client = newVerificationClient()) {
            HeadObjectResponse rawHead = client.headObject(HeadObjectRequest.builder()
                    .bucket(ossProperties.getBucketName())
                    .key(key)
                    .checksumMode("ENABLED")
                    .build()).join();

            assertThat(uploaded.size()).isEqualTo(content.length);
            assertThat(headed.bucket()).isEqualTo(ossProperties.getBucketName());
            assertThat(headed.key()).isEqualTo(key);
            assertThat(headed.size()).isEqualTo(content.length);
            assertThat(rawHead.metadata()).containsEntry("source", "integration");
            assertThat(rawHead.checksumSHA256()).isEqualTo(checksum);
            assertThat(client.getObjectTagging(GetObjectTaggingRequest.builder()
                            .bucket(ossProperties.getBucketName())
                            .key(key)
                            .build()).join().tagSet())
                    .anySatisfy(tag -> {
                        assertThat(tag.key()).isEqualTo("stage");
                        assertThat(tag.value()).isEqualTo("verified");
                    });
        }
    }

    @Test
    @DisplayName("两个并发上传写入同一 key 后对象应保持完整")
    void concurrentUploadsSameKey() throws Exception {
        String key = newTestDirectory() + "/concurrent.txt";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> first = submitConcurrentUpload(executor, ready, start, key, "first");
            Future<Throwable> second = submitConcurrentUpload(executor, ready, start, key, "second");
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Throwable> failures = Arrays.asList(first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS));
            assertThat(failures).containsOnlyNulls();
            assertThat(ossTemplate.query().getContent(key)).isIn("first", "second");
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("指定 Bucket 上传与跨 Bucket 复制应相互隔离")
    void uploadAndCopyAcrossBuckets() {
        String secondaryBucket = "oss-starter-" + UUID.randomUUID();
        String sourceKey = newTestDirectory() + "/multi-bucket-source.txt";
        String destinationKey = "multi-bucket/destination.txt";
        boolean bucketCreated = false;
        try {
            ossTemplate.put().createBucket(secondaryBucket);
            bucketCreated = true;
            ossTemplate.put().putObject(new PutObjectCommand(
                    null, sourceKey, new ByteArrayInputStream("source".getBytes(StandardCharsets.UTF_8)),
                    6L, "text/plain", null, null, null, false));
            StoredObject copied = ossTemplate.put().copyObject(new CopyObjectCommand(
                    ossProperties.getBucketName(), sourceKey, secondaryBucket, destinationKey));

            assertThat(copied.bucket()).isEqualTo(secondaryBucket);
            assertThat(ossTemplate.query().headObject(secondaryBucket, destinationKey).size()).isEqualTo(6L);
            assertThat(ossTemplate.query().getContent(secondaryBucket, destinationKey)).isEqualTo("source");
            assertThat(ossTemplate.query().checkExist(ossProperties.getBucketName(), destinationKey)).isFalse();
        } finally {
            if (bucketCreated) {
                try (S3AsyncClient client = newVerificationClient()) {
                    if (ossTemplate.query().checkExist(secondaryBucket, destinationKey)) {
                        ossTemplate.delete().removeObject(secondaryBucket, destinationKey);
                    }
                    client.deleteBucket(DeleteBucketRequest.builder().bucket(secondaryBucket).build()).join();
                }
            }
        }
    }

    @Test
    @DisplayName("超过阈值的对象命令应由标准 Async Client 自动 multipart 上传")
    void automaticMultipartUpload() {
        String key = newTestDirectory() + "/automatic-multipart.bin";
        long contentLength = 12L * 1024 * 1024;

        StoredObject uploaded = ossTemplate.put().putObject(new PutObjectCommand(
                null, key, new RepeatingInputStream(contentLength), contentLength,
                "application/octet-stream", null, null, null, false));

        assertThat(uploaded.size()).isEqualTo(contentLength);
        assertThat(uploaded.etag()).contains("-2");
        assertThat(ossTemplate.query().headObject(ossProperties.getBucketName(), key).size())
                .isEqualTo(contentLength);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OSS_RUN_LARGE_MULTIPART_TEST", matches = "true")
    @DisplayName("1 GB 以上对象应完成自动 multipart 上传")
    void automaticMultipartUploadLargerThanOneGigabyte() {
        String key = newTestDirectory() + "/large-automatic-multipart.bin";
        long contentLength = 1024L * 1024 * 1024 + 1;

        StoredObject uploaded = ossTemplate.put().putObject(new PutObjectCommand(
                null, key, new RepeatingInputStream(contentLength), contentLength,
                "application/octet-stream", null, null, null, false));

        assertThat(uploaded.size()).isEqualTo(contentLength);
        assertThat(uploaded.etag()).contains("-");
        assertThat(ossTemplate.query().headObject(ossProperties.getBucketName(), key).size())
                .isEqualTo(contentLength);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OSS_RUN_LARGE_COPY_TEST", matches = "true")
    @DisplayName("超过 5 GB 的对象应通过 multipart copy 完成服务端复制")
    void multipartServerSideCopyLargerThanSingleCopyLimit() {
        String directory = newTestDirectory();
        String sourceKey = directory + "/large-copy-source.bin";
        String destinationKey = directory + "/large-copy-destination.bin";
        long contentLength = 5_000_000_001L;
        OssClientOptions copyOptions = ossProperties.toOptions();
        copyOptions.setPartSizeInMb(512);
        copyOptions.setMaxConnections(4);
        OssTemplate largeCopyTemplate = new OssTemplate(copyOptions);
        try {
            StoredObject uploaded = largeCopyTemplate.put().putObject(new PutObjectCommand(
                    null, sourceKey, new RepeatingInputStream(contentLength), contentLength,
                    "application/octet-stream",
                    Map.of("source", "large-copy-integration"),
                    Map.of("stage", "multipart-copy"),
                    null, false));

            StoredObject copied = largeCopyTemplate.put().copyObject(new CopyObjectCommand(
                    null, sourceKey, null, destinationKey));

            assertThat(uploaded.size()).isEqualTo(contentLength);
            assertThat(copied.size()).isEqualTo(contentLength);
            try (S3AsyncClient client = newVerificationClient()) {
                HeadObjectResponse destination = client.headObject(HeadObjectRequest.builder()
                        .bucket(ossProperties.getBucketName())
                        .key(destinationKey)
                        .build()).join();
                assertThat(destination.contentLength()).isEqualTo(contentLength);
                assertThat(destination.metadata())
                        .containsEntry("source", "large-copy-integration");
                assertThat(client.getObjectTagging(GetObjectTaggingRequest.builder()
                                .bucket(ossProperties.getBucketName())
                                .key(destinationKey)
                                .build()).join().tagSet())
                        .anySatisfy(tag -> {
                            assertThat(tag.key()).isEqualTo("stage");
                            assertThat(tag.value()).isEqualTo("multipart-copy");
                        });
            }
        } finally {
            largeCopyTemplate.stop();
        }
    }

    @Test
    @DisplayName("复制与移动后对象位置应符合预期")
    void copyAndMoveObject() {
        String directory = newTestDirectory();
        String sourceKey = putTextObject(directory, "source.txt", "copy-move");

        String copiedKey = directory + "/copy/source.txt";
        ossTemplate.put().copyFile(sourceKey, copiedKey);
        ossTemplate.put().move(copiedKey, directory + "/moved");

        assertThat(ossTemplate.query().checkExist(sourceKey)).isTrue();
        assertThat(ossTemplate.query().checkExist(copiedKey)).isFalse();
        assertThat(ossTemplate.query().checkExist(directory + "/moved/source.txt")).isTrue();
        assertThat(ossTemplate.query().getContent(directory + "/moved/source.txt")).isEqualTo("copy-move");
        assertThat(ossTemplate.query().listObjects(".oss-staging/move/")).isEmpty();
    }

    @Test
    @DisplayName("分片初始化、上传与合并后应得到最终对象")
    void multipartUploadAndMerge() {
        String directory = newTestDirectory();
        byte[] firstPartBytes = new byte[5 * 1024 * 1024];
        Arrays.fill(firstPartBytes, (byte) 'a');
        ChunkTask chunkTask = new ChunkTask();
        chunkTask.setPath(directory);
        chunkTask.setFilename("big.txt");

        String uploadId = ossTemplate.put().initTask(chunkTask);

        ChunkUploadCommand firstChunk = new ChunkUploadCommand();
        firstChunk.setChunkNumber(1);
        firstChunk.setFilename("big.txt");
        firstChunk.setPath(directory);
        firstChunk.setGuid("guid-1");
        firstChunk.setUploadId(uploadId);
        firstChunk.setFileBytes(firstPartBytes);
        firstChunk.setContentLength(firstPartBytes.length);

        ChunkUploadCommand secondChunk = new ChunkUploadCommand();
        secondChunk.setChunkNumber(2);
        secondChunk.setFilename("big.txt");
        secondChunk.setPath(directory);
        secondChunk.setGuid("guid-1");
        secondChunk.setUploadId(uploadId);
        secondChunk.setFileBytes("world".getBytes(StandardCharsets.UTF_8));
        secondChunk.setContentLength("world".getBytes(StandardCharsets.UTF_8).length);

        ChunkTarget part1 = ossTemplate.put().chunk(firstChunk);
        ChunkTarget part2 = ossTemplate.put().chunk(secondChunk);

        ChunkMerge chunkMerge = new ChunkMerge();
        chunkMerge.setFilename("big.txt");
        chunkMerge.setPath(directory);
        chunkMerge.setGuid("guid-1");
        chunkMerge.setUploadId(uploadId);
        chunkMerge.setExpectedPartCount(2);
        chunkMerge.setExpectedSize((long) firstPartBytes.length + secondChunk.getContentLength());
        chunkMerge.setChunkTargetList(List.of(part2, part1));

        ObjectInfo merged = ossTemplate.put().merge(chunkMerge);

        assertThat(merged.getUri()).isEqualTo(directory + "/big.txt");
        assertThat(ossTemplate.query().getContent(directory + "/big.txt"))
                .startsWith("aaaa")
                .endsWith("world");
    }

    @Test
    @DisplayName("默认 bucket 启动后应自动创建")
    void autoCreateBucketOnStartup() {
        assertThat(ossTemplate.query().testConnectForBucket()).isTrue();
    }

    private Future<Throwable> submitConcurrentUpload(ExecutorService executor, CountDownLatch ready,
                                                     CountDownLatch start, String key, String content) {
        return executor.submit(() -> {
            ready.countDown();
            try {
                start.await();
                byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                ossTemplate.put().putObject(new PutObjectCommand(
                        null, key, new ByteArrayInputStream(bytes), (long) bytes.length,
                        "text/plain", null, null, null, false));
                return null;
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                return failure;
            } catch (RuntimeException failure) {
                return failure;
            }
        });
    }

    private S3AsyncClient newVerificationClient() {
        return S3AsyncClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        ossProperties.getAccessKey(), ossProperties.getSecretKey())))
                .endpointOverride(URI.create(ossProperties.getEndpoint()))
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .build();
    }

    private static final class RepeatingInputStream extends InputStream {
        private long remaining;

        private RepeatingInputStream(long contentLength) {
            this.remaining = contentLength;
        }

        @Override
        public int read() {
            if (remaining == 0) {
                return -1;
            }
            remaining--;
            return 'a';
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            if (remaining == 0) {
                return -1;
            }
            int bytesToRead = (int) Math.min(remaining, length);
            Arrays.fill(bytes, offset, offset + bytesToRead, (byte) 'a');
            remaining -= bytesToRead;
            return bytesToRead;
        }
    }
}
