package com.wiblog.oss.service;

import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.bean.UnzipResult;
import com.wiblog.oss.util.Util;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 流式解压操作类。
 *
 * 通过 {@link ZipInputStream} 边下载边解压，避免将整个 ZIP 文件先下载到本地再解压，
 * 大幅降低内存占用和磁盘 I/O。每个 ZIP 条目解压后立即以多部分上传写回 S3 目标路径。
 *
 * <h3>工作流程</h3>
 * <pre>
 *   S3 ZIP 对象
 *       ↓  GetObject → InputStream
 *   ZipInputStream
 *       ↓  nextEntry() 循环
 *   [每个 ZipEntry]
 *       ↓  读取内容 → 写入 PipedOutputStream
 *   PutObject（目标路径）
 * </pre>
 *
 * <h3>内存策略</h3>
 * <ul>
 *   <li>对于已知大小的条目（{@code entry.getSize() >= 0}），使用精确缓冲区直传。</li>
 *   <li>对于未知大小的条目（压缩包内再嵌套 ZIP 等极端情况），先写入 ByteArrayOutputStream
 *       缓冲再上传，以换取稳定性。</li>
 * </ul>
 *
 * @author panwm
 */
@Slf4j
public class StreamUnzipOperations extends Operations {

    /**
     * 流式读取时的 IO 缓冲区（64KB），平衡内存与吞吐。
     */
    private static final int BUFFER_SIZE = 64 * 1024;

    /**
     * 单个条目内存缓冲上限（128MB）。超过此大小的未知长度条目将写临时文件。
     */
    private static final int MAX_BUFFER_BYTES = 128 * 1024 * 1024;

    public StreamUnzipOperations(OssProperties ossProperties, S3AsyncClient client,
                                 S3TransferManager transferManager) {
        super(ossProperties, client, transferManager);
    }

    // ----------------------------------------------------------------
    // 公开 API
    // ----------------------------------------------------------------

    /**
     * 将 S3 中的 ZIP 对象流式解压到同一 Bucket 的目标路径。
     *
     * @param zipObjectKey ZIP 文件在 S3 中的 key
     * @param targetPath   解压后文件的目标路径前缀（结尾无需加 "/"）
     * @return 解压结果，包含成功/失败列表
     */
    public UnzipResult unzip(String zipObjectKey, String targetPath) {
        return unzip(ossProperties.getBucketName(), zipObjectKey, ossProperties.getBucketName(), targetPath);
    }

    /**
     * 将源 Bucket 的 ZIP 对象流式解压到目标 Bucket 的指定路径。
     *
     * @param sourceBucket 源 Bucket
     * @param zipObjectKey ZIP 文件 key
     * @param targetBucket 目标 Bucket
     * @param targetPath   目标路径前缀
     * @return 解压结果
     */
    public UnzipResult unzip(String sourceBucket, String zipObjectKey,
                             String targetBucket, String targetPath) {
        String normalizedTargetPath = Util.formatPath(targetPath);
        log.info("Stream unzip: [{}/{}] → [{}/{}]",
                sourceBucket, zipObjectKey, targetBucket, normalizedTargetPath);

        List<ObjectInfo> succeeded = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        try (InputStream s3Stream = fetchInputStream(sourceBucket, zipObjectKey);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(s3Stream, BUFFER_SIZE))) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String entryName = entry.getName();
                String destKey = normalizedTargetPath + entryName;
                try {
                    ObjectInfo info = uploadEntry(zis, entry, targetBucket, destKey);
                    succeeded.add(info);
                    log.debug("Unzipped entry [{}] → [{}]", entryName, destKey);
                } catch (Exception ex) {
                    log.warn("Failed to unzip entry [{}]: {}", entryName, ex.getMessage(), ex);
                    failed.add(entryName);
                } finally {
                    zis.closeEntry();
                }
            }
        } catch (IOException e) {
            log.error("Stream unzip failed for [{}]: {}", zipObjectKey, e.getMessage(), e);
            throw new com.wiblog.oss.exception.OssException("UNZIP_ERROR",
                    "Stream unzip failed: " + e.getMessage(), e);
        }

        log.info("Stream unzip done: {} succeeded, {} failed", succeeded.size(), failed.size());
        return UnzipResult.builder()
                .targetPath(normalizedTargetPath)
                .succeeded(succeeded)
                .failed(failed)
                .build();
    }

    /**
     * 将 S3 中的 ZIP 对象流式解压，每个条目由调用方通过 {@link UnzipEntryHandler} 自定义处理。
     *
     * <p>适用于解压后写入本地磁盘、转发到 HTTP 响应等自定义场景。
     *
     * @param zipObjectKey ZIP 文件 key
     * @param handler      自定义条目处理器
     * @return 解压结果
     */
    public UnzipResult unzip(String zipObjectKey, UnzipEntryHandler handler) {
        return unzip(ossProperties.getBucketName(), zipObjectKey, handler);
    }

    /**
     * 将指定 Bucket 中的 ZIP 对象流式解压，条目由 handler 自定义处理。
     *
     * @param bucketName   源 Bucket
     * @param zipObjectKey ZIP 文件 key
     * @param handler      自定义条目处理器
     * @return 解压结果
     */
    public UnzipResult unzip(String bucketName, String zipObjectKey, UnzipEntryHandler handler) {
        log.info("Stream unzip with custom handler: [{}/{}]", bucketName, zipObjectKey);

        List<ObjectInfo> succeeded = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        try (InputStream s3Stream = fetchInputStream(bucketName, zipObjectKey);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(s3Stream, BUFFER_SIZE))) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String entryName = entry.getName();
                try {
                    // 用 NonClosingInputStream 包装，防止 handler 误关闭 zis
                    handler.handle(entry, new NonClosingInputStream(zis));
                    succeeded.add(ObjectInfo.builder()
                            .name(Util.getFilename(entryName))
                            .uri(entryName)
                            .size(entry.getSize() < 0 ? -1 : entry.getSize())
                            .build());
                    log.debug("Handled entry [{}]", entryName);
                } catch (Exception ex) {
                    log.warn("Handler failed for entry [{}]: {}", entryName, ex.getMessage(), ex);
                    failed.add(entryName);
                } finally {
                    zis.closeEntry();
                }
            }
        } catch (IOException e) {
            log.error("Stream unzip (custom handler) failed for [{}]: {}", zipObjectKey, e.getMessage(), e);
            throw new com.wiblog.oss.exception.OssException("UNZIP_ERROR",
                    "Stream unzip failed: " + e.getMessage(), e);
        }

        return UnzipResult.builder()
                .succeeded(succeeded)
                .failed(failed)
                .build();
    }

    /**
     * 仅解压 ZIP 包中指定路径前缀的条目到目标路径。
     *
     * @param zipObjectKey ZIP 文件 key
     * @param entryPrefix  只解压名称以此前缀开头的条目，传 null 或 "" 表示全部
     * @param targetPath   目标路径前缀
     * @return 解压结果
     */
    public UnzipResult unzipWithFilter(String zipObjectKey, String entryPrefix, String targetPath) {
        return unzipWithFilter(ossProperties.getBucketName(), zipObjectKey,
                ossProperties.getBucketName(), entryPrefix, targetPath);
    }

    /**
     * 带前缀过滤的跨 Bucket 流式解压。
     */
    public UnzipResult unzipWithFilter(String sourceBucket, String zipObjectKey,
                                       String targetBucket, String entryPrefix, String targetPath) {
        final String prefix = (entryPrefix == null) ? "" : entryPrefix;
        return unzip(sourceBucket, zipObjectKey, (entry, stream) -> {
            if (!entry.getName().startsWith(prefix)) {
                return; // 跳过不匹配的条目
            }
            String relName = prefix.isEmpty() ? entry.getName()
                    : entry.getName().substring(prefix.length());
            String destKey = Util.formatPath(targetPath) + relName;
            byte[] data = stream.readAllBytes();
            uploadBytes(data, targetBucket, destKey);
        });
    }

    // ----------------------------------------------------------------
    // 私有实现
    // ----------------------------------------------------------------

    /**
     * 从 S3 获取对象输入流。
     */
    private InputStream fetchInputStream(String bucketName, String objectKey) {
        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(bucketName).key(objectKey).build();
        byte[] bytes = handleRequest(() ->
                client.getObject(req, AsyncResponseTransformer.toBytes())
                        .thenApply(rb -> {
                            ByteBuffer buf = rb.asByteBuffer();
                            byte[] b = new byte[buf.remaining()];
                            buf.get(b);
                            return b;
                        }));
        return new ByteArrayInputStream(bytes);
    }

    /**
     * 将 ZIP 条目内容上传到 S3 目标 key。
     *
     * <p>优先使用条目的已知大小（{@code entry.getSize()}）做精确传输；
     * 如果为 -1（未知大小，通常是 DEFLATED 压缩方法），则先读入内存再上传。</p>
     */
    private ObjectInfo uploadEntry(ZipInputStream zis, ZipEntry entry,
                                   String targetBucket, String destKey) throws IOException {
        long knownSize = entry.getSize();
        byte[] data = readAllBytes(zis);
        uploadBytes(data, targetBucket, destKey);

        long actualSize = knownSize >= 0 ? knownSize : data.length;
        return ObjectInfo.builder()
                .uri(destKey)
                .url(getDomain() + destKey)
                .name(Util.getFilename(destKey))
                .size(actualSize)
                .ext(Util.getExtension(destKey))
                .uploadTime(new Date())
                .build();
    }

    /**
     * 将字节数组上传到 S3。
     */
    private void uploadBytes(byte[] data, String bucket, String key) {
        PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(Util.getContentType(key))
                .contentLength((long) data.length)
                .build();
        handleRequest(() -> client.putObject(putReq, AsyncRequestBody.fromBytes(data)));
        log.debug("Uploaded unzipped entry: [{}/{}] ({} bytes)", bucket, key, data.length);
    }

    /**
     * 从流中读取全部字节（不关闭流）。
     */
    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[BUFFER_SIZE];
        int read;
        while ((read = in.read(buf)) != -1) {
            baos.write(buf, 0, read);
            if (baos.size() > MAX_BUFFER_BYTES) {
                throw new IOException("ZIP entry exceeds max buffer size: " + MAX_BUFFER_BYTES + " bytes");
            }
        }
        return baos.toByteArray();
    }

    /**
     * 包装输入流，禁止调用方调用 close()，防止误关闭 ZipInputStream。
     */
    private static class NonClosingInputStream extends FilterInputStream {
        NonClosingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() {
            // 刻意不关闭，由框架统一管理
        }
    }
}