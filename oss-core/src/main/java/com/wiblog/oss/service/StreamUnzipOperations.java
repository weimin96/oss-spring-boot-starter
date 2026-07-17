package com.wiblog.oss.service;

import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.UnzipResult;
import com.wiblog.oss.config.OssClientOptions;
import com.wiblog.oss.exception.OssException;
import com.wiblog.oss.util.Util;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.BlockingInputStreamAsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 流式解压操作类。
 * <p>
 * 通过 ZipInputStream 边下载边解压，避免把整个 ZIP 文件先落盘到本地。
 *
 * @author panwm
 */
@Slf4j
public class StreamUnzipOperations extends Operations implements OssUnzipService {

    /**
     * 流式读取缓冲区大小。
     */
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_UNZIP_ENTRIES = 10000;
    private static final long MAX_UNZIP_ENTRY_BYTES = 5L * 1024 * 1024 * 1024;
    private static final long MAX_UNZIP_TOTAL_BYTES = 50L * 1024 * 1024 * 1024;

    /**
     * 创建流式解压操作门面。
     *
     * @param ossProperties   OSS 配置
     * @param client          S3 异步客户端
     * @param transferManager 传输管理器
     */
    public StreamUnzipOperations(OssClientOptions ossProperties, S3AsyncClient client,
                                 S3TransferManager transferManager) {
        super(ossProperties, client, transferManager);
    }

    /**
     * 在默认 Bucket 内把 ZIP 对象解压到默认 Bucket 的目标目录。
     *
     * @param zipObjectKey ZIP 对象 key
     * @param targetPath   目标目录前缀
     * @return 解压结果
     */
    @Override
    public UnzipResult unzip(String zipObjectKey, String targetPath) {
        return unzip(ossProperties.getBucketName(), zipObjectKey, ossProperties.getBucketName(), targetPath);
    }

    /**
     * 在指定源/目标 Bucket 之间执行流式解压。
     *
     * <p>该方法按条目边读取边上传，
     * 目的是避免先把整个 ZIP 下载到本地，降低临时磁盘依赖和大文件内存峰值。</p>
     *
     * @param sourceBucket 源 Bucket
     * @param zipObjectKey ZIP 对象 key
     * @param targetBucket 目标 Bucket
     * @param targetPath   目标目录前缀
     * @return 解压结果
     */
    @Override
    public UnzipResult unzip(String sourceBucket, String zipObjectKey,
                             String targetBucket, String targetPath) {
        String normalizedTargetPath = Util.normalizeObjectPrefix(targetPath);
        log.info("Stream unzip: [{}/{}] -> [{}/{}]",
                sourceBucket, zipObjectKey, targetBucket, normalizedTargetPath);

        List<ObjectInfo> succeeded = new ArrayList<ObjectInfo>();
        List<String> failed = new ArrayList<String>();
        UnzipBudget budget = new UnzipBudget();

        try (InputStream s3Stream = fetchInputStream(sourceBucket, zipObjectKey);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(s3Stream, BUFFER_SIZE))) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = normalizeZipEntryName(entry.getName());
                if (entry.isDirectory()) {
                    budget.startEntry(entry);
                    zis.closeEntry();
                    continue;
                }

                String destKey = Util.normalizeObjectKey(normalizedTargetPath + entryName);
                InputStream entryInputStream = openLimitedEntryStream(zis, entry, budget);
                try {
                    ObjectInfo info = uploadEntry(entryInputStream, entry, targetBucket, destKey);
                    succeeded.add(info);
                    log.debug("Unzipped entry [{}] -> [{}]", entryName, destKey);
                } catch (Exception ex) {
                    rethrowFatalUnzipFailure(ex);
                    log.warn("Failed to unzip entry [{}]: {}", entryName, ex.getMessage(), ex);
                    failed.add(entryName);
                } finally {
                    zis.closeEntry();
                }
            }
        } catch (IOException e) {
            log.error("Stream unzip failed for [{}]: {}", zipObjectKey, e.getMessage(), e);
            throw new OssException("UNZIP_ERROR",
                    "Stream unzip failed: " + e.getMessage(), e);
        }

        return UnzipResult.builder()
                .targetPath(normalizedTargetPath)
                .succeeded(succeeded)
                .failed(failed)
                .build();
    }

    /**
     * 在默认 Bucket 内按自定义处理器消费 ZIP 条目。
     *
     * @param zipObjectKey ZIP 对象 key
     * @param handler      条目处理器
     * @return 处理结果
     */
    @Override
    public UnzipResult unzip(String zipObjectKey, UnzipEntryHandler handler) {
        return unzip(ossProperties.getBucketName(), zipObjectKey, handler);
    }

    /**
     * 在指定 Bucket 内按自定义处理器消费 ZIP 条目。
     *
     * <p>该重载适用于”读取 ZIP 条目后不直接上传，而是交由业务方自定义处理”的场景，
     * 例如筛选、转码或二次写入其他系统。</p>
     *
     * @param bucketName   Bucket 名称
     * @param zipObjectKey ZIP 对象 key
     * @param handler      条目处理器
     * @return 处理结果
     */
    @Override
    public UnzipResult unzip(String bucketName, String zipObjectKey, UnzipEntryHandler handler) {
        log.info("Stream unzip with custom handler: [{}/{}]", bucketName, zipObjectKey);

        List<ObjectInfo> succeeded = new ArrayList<ObjectInfo>();
        List<String> failed = new ArrayList<String>();
        UnzipBudget budget = new UnzipBudget();

        try (InputStream s3Stream = fetchInputStream(bucketName, zipObjectKey);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(s3Stream, BUFFER_SIZE))) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = normalizeZipEntryName(entry.getName());
                if (entry.isDirectory()) {
                    budget.startEntry(entry);
                    zis.closeEntry();
                    continue;
                }

                InputStream entryInputStream = openLimitedEntryStream(zis, entry, budget);
                try {
                    handler.handle(entry, entryInputStream);
                    drain(entryInputStream);
                    succeeded.add(ObjectInfo.builder()
                            .name(Util.getFilename(entryName))
                            .uri(entryName)
                            .size(entry.getSize() < 0 ? -1 : entry.getSize())
                            .build());
                    log.debug("Handled entry [{}]", entryName);
                } catch (Exception ex) {
                    rethrowFatalUnzipFailure(ex);
                    log.warn("Handler failed for entry [{}]: {}", entryName, ex.getMessage(), ex);
                    failed.add(entryName);
                } finally {
                    zis.closeEntry();
                }
            }
        } catch (IOException e) {
            log.error("Stream unzip (custom handler) failed for [{}]: {}", zipObjectKey, e.getMessage(), e);
            throw new OssException("UNZIP_ERROR",
                    "Stream unzip failed: " + e.getMessage(), e);
        }

        return UnzipResult.builder()
                .succeeded(succeeded)
                .failed(failed)
                .build();
    }

    /**
     * 在默认 Bucket 内按条目前缀过滤并解压 ZIP。
     *
     * @param zipObjectKey ZIP 对象 key
     * @param entryPrefix  条目前缀；为空时表示不过滤
     * @param targetPath   目标目录前缀
     * @return 解压结果
     */
    @Override
    public UnzipResult unzipWithFilter(String zipObjectKey, String entryPrefix, String targetPath) {
        return unzipWithFilter(ossProperties.getBucketName(), zipObjectKey,
                ossProperties.getBucketName(), entryPrefix, targetPath);
    }

    /**
     * 过滤解压必须只记录真正命中过滤条件且成功上传的条目。
     * <p>
     * 这里不再复用”自定义 handler 解压”分支，因为那个分支会把每个非目录条目都计入 succeeded，
     * 无法区分”被过滤跳过”和”实际已解压”。
     *
     * @param sourceBucket 源 Bucket
     * @param zipObjectKey ZIP 对象 key
     * @param targetBucket 目标 Bucket
     * @param entryPrefix  条目前缀；为空时表示不过滤
     * @param targetPath   目标目录前缀
     * @return 解压结果
     */
    @Override
    public UnzipResult unzipWithFilter(String sourceBucket, String zipObjectKey,
                                       String targetBucket, String entryPrefix, String targetPath) {
        final String prefix = normalizeZipEntryPrefix(entryPrefix);
        final String normalizedTargetPath = Util.normalizeObjectPrefix(targetPath);
        List<ObjectInfo> succeeded = new ArrayList<ObjectInfo>();
        List<String> failed = new ArrayList<String>();
        UnzipBudget budget = new UnzipBudget();

        try (InputStream s3Stream = fetchInputStream(sourceBucket, zipObjectKey);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(s3Stream, BUFFER_SIZE))) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = normalizeZipEntryName(entry.getName());
                budget.startEntry(entry);
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                if (!entryName.startsWith(prefix)) {
                    zis.closeEntry();
                    continue;
                }

                InputStream entryInputStream = new QuotaInputStream(
                        new NonClosingInputStream(zis), budget);
                try {
                    String relativeName = prefix.isEmpty() ? entryName : entryName.substring(prefix.length());
                    if (Util.isBlank(relativeName)) {
                        throw new OssException("UNZIP_ENTRY_INVALID",
                                "过滤后的 ZIP 条目名称不能为空：" + entryName);
                    }
                    String destKey = Util.normalizeObjectKey(normalizedTargetPath + relativeName);
                    ObjectInfo info = uploadEntry(entryInputStream, entry, targetBucket, destKey);
                    succeeded.add(info);
                    log.debug("Filtered unzip entry [{}] -> [{}]", entryName, destKey);
                } catch (Exception ex) {
                    rethrowFatalUnzipFailure(ex);
                    log.warn("Failed to unzip filtered entry [{}]: {}", entryName, ex.getMessage(), ex);
                    failed.add(entryName);
                } finally {
                    zis.closeEntry();
                }
            }
        } catch (IOException e) {
            log.error("Filtered stream unzip failed for [{}]: {}", zipObjectKey, e.getMessage(), e);
            throw new OssException("UNZIP_ERROR",
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
        return requireSuccessfulRequest(() ->
                client.getObject(req, AsyncResponseTransformer.toBlockingInputStream()),
                "UNZIP_SOURCE_READ_FAILED",
                "读取 ZIP 对象失败：" + objectKey);
    }

    private ObjectInfo uploadEntry(InputStream entryInputStream, ZipEntry entry,
                                   String targetBucket, String destKey) throws IOException {
        long knownSize = entry.getSize();
        long actualSize = uploadStream(entryInputStream, knownSize, targetBucket, destKey);

        return ObjectInfo.builder()
                .uri(destKey)
                .url(getDomain() + destKey)
                .name(Util.getFilename(destKey))
                .size(actualSize)
                .ext(Util.getExtension(destKey))
                .uploadTime(new Date())
                .build();
    }

    private long uploadStream(InputStream inputStream, long knownSize, String bucket, String key) {
        Long contentLength = knownSize >= 0 ? knownSize : null;
        PutObjectRequest.Builder putRequestBuilder = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(Util.getContentType(key));
        if (contentLength != null) {
            putRequestBuilder.contentLength(contentLength);
        }
        BlockingInputStreamAsyncRequestBody body = AsyncRequestBody.forBlockingInputStream(contentLength);
        CompletableFuture<PutObjectResponse> uploadFuture = client.putObject(putRequestBuilder.build(), body);
        long actualSize;
        try {
            actualSize = body.writeInputStream(inputStream);
            requireSuccessfulRequest(() -> uploadFuture,
                    "UNZIP_ENTRY_UPLOAD_FAILED",
                    "上传解压条目失败：" + key);
        } catch (OssException e) {
            body.cancel();
            throw e;
        } catch (RuntimeException e) {
            body.cancel();
            throw OssException.uploadFailed(key, e);
        }
        log.debug("Uploaded unzipped entry: [{}/{}] ({} bytes)", bucket, key, actualSize);
        return actualSize;
    }

    private void drain(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        while (inputStream.read(buffer) != -1) {
            // 读取剩余条目内容以确保配额统计覆盖整个条目。
        }
    }

    private String normalizeZipEntryName(String rawEntryName) {
        if (Util.isBlank(rawEntryName)) {
            throw new OssException("UNZIP_ENTRY_INVALID", "ZIP 条目名称不能为空");
        }
        String normalized = rawEntryName.replace('\\', '/');
        if (normalized.startsWith("/")
                || (normalized.length() >= 2
                && Character.isLetter(normalized.charAt(0))
                && normalized.charAt(1) == ':')) {
            throw new OssException("UNZIP_ENTRY_INVALID",
                    "ZIP 条目不能使用绝对路径：" + rawEntryName);
        }
        StringBuilder safeName = new StringBuilder();
        String[] segments = normalized.split("/");
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            if (".".equals(segment) || "..".equals(segment)) {
                throw new OssException("UNZIP_ENTRY_INVALID",
                        "ZIP 条目包含非法路径段：" + rawEntryName);
            }
            if (safeName.length() > 0) {
                safeName.append('/');
            }
            safeName.append(segment);
        }
        if (safeName.length() == 0) {
            throw new OssException("UNZIP_ENTRY_INVALID",
                    "ZIP 条目名称无效：" + rawEntryName);
        }
        return safeName.toString();
    }

    private String normalizeZipEntryPrefix(String entryPrefix) {
        if (Util.isBlank(entryPrefix)) {
            return "";
        }
        String rawPrefix = entryPrefix.trim().replace('\\', '/');
        boolean directoryPrefix = rawPrefix.endsWith("/");
        String normalized = normalizeZipEntryName(rawPrefix);
        return directoryPrefix ? normalized + "/" : normalized;
    }

    private InputStream openLimitedEntryStream(ZipInputStream inputStream, ZipEntry entry,
                                               UnzipBudget budget) {
        budget.startEntry(entry);
        return new QuotaInputStream(new NonClosingInputStream(inputStream), budget);
    }

    private void rethrowFatalUnzipFailure(Exception exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof OssException) {
                String code = ((OssException) current).getCode();
                if ("UNZIP_LIMIT_EXCEEDED".equals(code) || "UNZIP_ENTRY_INVALID".equals(code)) {
                    throw (OssException) current;
                }
            }
            current = current.getCause();
        }
    }

    private static final class UnzipBudget {
        private int entryCount;
        private long totalBytes;

        private void startEntry(ZipEntry entry) {
            entryCount++;
            if (entryCount > MAX_UNZIP_ENTRIES) {
                throw new OssException("UNZIP_LIMIT_EXCEEDED",
                        "ZIP 条目数量超过限制：" + MAX_UNZIP_ENTRIES);
            }
            long declaredSize = entry.getSize();
            if (declaredSize > MAX_UNZIP_ENTRY_BYTES) {
                throw new OssException("UNZIP_LIMIT_EXCEEDED",
                        "ZIP 单条目大小超过限制：" + entry.getName());
            }
            if (declaredSize >= 0 && declaredSize > MAX_UNZIP_TOTAL_BYTES - totalBytes) {
                throw new OssException("UNZIP_LIMIT_EXCEEDED",
                        "ZIP 解压总大小超过限制");
            }
        }

        private void record(long bytes, long entryBytes) {
            if (entryBytes > MAX_UNZIP_ENTRY_BYTES) {
                throw new OssException("UNZIP_LIMIT_EXCEEDED",
                        "ZIP 单条目实际大小超过限制");
            }
            if (bytes > MAX_UNZIP_TOTAL_BYTES - totalBytes) {
                throw new OssException("UNZIP_LIMIT_EXCEEDED",
                        "ZIP 解压总大小超过限制");
            }
            totalBytes += bytes;
        }
    }

    private static final class QuotaInputStream extends FilterInputStream {
        private final UnzipBudget budget;
        private long entryBytes;

        private QuotaInputStream(InputStream inputStream, UnzipBudget budget) {
            super(inputStream);
            this.budget = budget;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                record(1L);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            if (read > 0) {
                record(read);
            }
            return read;
        }

        private void record(long bytes) {
            entryBytes += bytes;
            budget.record(bytes, entryBytes);
        }
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


