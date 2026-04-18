package com.wiblog.oss.service;

import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.chunk.ChunkMerge;
import com.wiblog.oss.bean.chunk.ChunkTarget;
import com.wiblog.oss.bean.chunk.ChunkTask;
import com.wiblog.oss.bean.chunk.ChunkUploadCommand;
import com.wiblog.oss.support.AbstractServiceDynamicPropertyTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

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
}
