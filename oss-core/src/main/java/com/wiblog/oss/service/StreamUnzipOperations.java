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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 流式解压操作类。
 *
 * 通过 ZipInputStream 边下载边解压，避免把整个 ZIP 文件先落盘到本地。
 *
 * @author panwm
 */
@Slf4j
public class StreamUnzipOperations extends Operations {

    /**
     * 流式读取缓冲区大小。
     */
    private static final int BUFFER_SIZE = 64 * 1024;

    /**
     * 单个 ZIP 条目允许进入内存缓冲的上限。
     *
     * 这里显式限制，是为了避免过滤解压和自定义处理在遇到异常大条目时把 JVM 内存拖垮。
     */
    private static final int MAX_BUFFER_BYTES = 128 * 1024 * 1024;

    public StreamUnzipOperations(OssProperties ossProperties, S3AsyncClient client,
                                 S3TransferManager transferManager) {
        super(ossProperties, client, transferManager);
    }

    public UnzipResult unzip(String zipObjectKey, String targetPath) {
        return unzip(ossProperties.getBucketName(), zipObjectKey, ossProperties.getBucketName(), targetPath);
    }

    public UnzipResult unzip(String sourceBucket, String zipObjectKey,
                             String targetBucket, String targetPath) {
        String normalizedTargetPath = Util.formatPath(targetPath);
        log.info("Stream unzip: [{}/{}] -> [{}/{}]",
                sourceBucket, zipObjectKey, targetBucket, normalizedTargetPath);

        List<ObjectInfo> succeeded = new ArrayList<ObjectInfo>();
        List<String> failed = new ArrayList<String>();

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
                    log.debug("Unzipped entry [{}] -> [{}]", entryName, destKey);
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

        return UnzipResult.builder()
                .targetPath(normalizedTargetPath)
                .succeeded(succeeded)
                .failed(failed)
                .build();
    }

    public UnzipResult unzip(String zipObjectKey, UnzipEntryHandler handler) {
        return unzip(ossProperties.getBucketName(), zipObjectKey, handler);
    }

    public UnzipResult unzip(String bucketName, String zipObjectKey, UnzipEntryHandler handler) {
        log.info("Stream unzip with custom handler: [{}/{}]", bucketName, zipObjectKey);

        List<ObjectInfo> succeeded = new ArrayList<ObjectInfo>();
        List<String> failed = new ArrayList<String>();

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

    public UnzipResult unzipWithFilter(String zipObjectKey, String entryPrefix, String targetPath) {
        return unzipWithFilter(ossProperties.getBucketName(), zipObjectKey,
                ossProperties.getBucketName(), entryPrefix, targetPath);
    }

    /**
     * 过滤解压必须只记录真正命中过滤条件且成功上传的条目。
     *
     * 这里不再复用“自定义 handler 解压”分支，因为那个分支会把每个非目录条目都计入 succeeded，
     * 无法区分“被过滤跳过”和“实际已解压”。
     */
    public UnzipResult unzipWithFilter(String sourceBucket, String zipObjectKey,
                                       String targetBucket, String entryPrefix, String targetPath) {
        final String prefix = entryPrefix == null ? "" : entryPrefix;
        final String normalizedTargetPath = Util.formatPath(targetPath);
        List<ObjectInfo> succeeded = new ArrayList<ObjectInfo>();
        List<String> failed = new ArrayList<String>();

        try (InputStream s3Stream = fetchInputStream(sourceBucket, zipObjectKey);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(s3Stream, BUFFER_SIZE))) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                String entryName = entry.getName();
                if (!entryName.startsWith(prefix)) {
                    zis.closeEntry();
                    continue;
                }

                try {
                    String relativeName = prefix.isEmpty() ? entryName : entryName.substring(prefix.length());
                    while (relativeName.startsWith("/")) {
                        relativeName = relativeName.substring(1);
                    }
                    String destKey = normalizedTargetPath + relativeName;
                    ObjectInfo info = uploadEntry(zis, entry, targetBucket, destKey);
                    succeeded.add(info);
                    log.debug("Filtered unzip entry [{}] -> [{}]", entryName, destKey);
                } catch (Exception ex) {
                    log.warn("Failed to unzip filtered entry [{}]: {}", entryName, ex.getMessage(), ex);
                    failed.add(entryName);
                } finally {
                    zis.closeEntry();
                }
            }
        } catch (IOException e) {
            log.error("Filtered stream unzip failed for [{}]: {}", zipObjectKey, e.getMessage(), e);
            throw new com.wiblog.oss.exception.OssException("UNZIP_ERROR",
                    "Stream unzip failed: " + e.getMessage(), e);
        }

        return UnzipResult.builder()
                .targetPath(normalizedTargetPath)
                .succeeded(succeeded)
                .failed(failed)
                .build();
    }

    private InputStream fetchInputStream(String bucketName, String objectKey) {
        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();
        byte[] bytes = handleRequest(() ->
                client.getObject(req, AsyncResponseTransformer.toBytes())
                        .thenApply(responseBytes -> {
                            ByteBuffer byteBuffer = responseBytes.asByteBuffer();
                            byte[] buffer = new byte[byteBuffer.remaining()];
                            byteBuffer.get(buffer);
                            return buffer;
                        }));
        return new ByteArrayInputStream(bytes);
    }

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
     * 包装输入流，防止 handler 错误关闭底层 ZipInputStream。
     */
    private static class NonClosingInputStream extends FilterInputStream {

        NonClosingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() {
            // 故意不关闭，统一由外层 ZipInputStream 管理生命周期。
        }
    }
}
