package com.wiblog.oss.service;

import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.bean.chunk.Chunk;
import com.wiblog.oss.bean.chunk.ChunkMerge;
import com.wiblog.oss.bean.chunk.ChunkTarget;
import com.wiblog.oss.bean.chunk.ChunkTask;
import com.wiblog.oss.exception.OssException;
import com.wiblog.oss.util.Util;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.BlockingInputStreamAsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.Upload;
import software.amazon.awssdk.transfer.s3.model.UploadDirectoryRequest;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;
import software.amazon.awssdk.transfer.s3.model.UploadRequest;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 上传操作
 *
 * @author panwm
 */
@Slf4j
public class PutOperations extends Operations {

    public PutOperations(OssProperties ossProperties, S3AsyncClient client, S3TransferManager transferManager) {
        super(ossProperties, client, transferManager);
    }

    // ----------------------------------------------------------------
    // Bucket 操作
    // ----------------------------------------------------------------

    /**
     * 创建 bucket（若已存在则跳过）
     */
    public void createBucket(String bucketName) {
        if (!bucketExists(bucketName)) {
            CreateBucketRequest req = CreateBucketRequest.builder().bucket(bucketName).build();
            handleRequest(() -> client.createBucket(req));
            log.info("Bucket [{}] created", bucketName);
        }
    }

    private boolean bucketExists(String bucketName) {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build()).join();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ----------------------------------------------------------------
    // 文件上传 - InputStream
    // ----------------------------------------------------------------

    public ObjectInfo putObject(String path, String filename, InputStream in) {
        return putObject(ossProperties.getBucketName(), path, filename, in);
    }

    public ObjectInfo putObject(String bucketName, String path, String filename, InputStream in) {
        return putObjectForKey(bucketName, formatPath(path) + filename, in);
    }

    public ObjectInfo putObjectForKey(String objectName, InputStream stream) {
        return putObjectForKey(ossProperties.getBucketName(), objectName, stream);
    }

    /**
     * 上传 InputStream。
     * <p>
     * 改进：原代码使用 stream.available() 获取大小（不可靠），
     * 现改为先将流读入缓冲区，用精确字节数上传，确保 Content-Length 正确。
     */
    public ObjectInfo putObjectForKey(String bucketName, String objectName, InputStream stream) {
        objectName = formatPath(objectName);
        // 先缓冲，获得精确长度
        byte[] data = toByteArray(stream);
        long fileSize = data.length;

        BlockingInputStreamAsyncRequestBody body = AsyncRequestBody.forBlockingInputStream(fileSize);
        PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(bucketName).key(objectName)
                .contentType(Util.getContentType(objectName))
                .build();
        UploadRequest uploadReq = UploadRequest.builder()
                .requestBody(body).putObjectRequest(putReq).build();

        Upload upload = transferManager.upload(uploadReq);
        body.writeInputStream(new ByteArrayInputStream(data));
        upload.completionFuture().join();

        return buildObjectInfo(objectName, new Date(), fileSize);
    }

    // ----------------------------------------------------------------
    // 文件上传 - File
    // ----------------------------------------------------------------

    public ObjectInfo putObject(String path, String filename, File file) {
        return putObject(ossProperties.getBucketName(), path, filename, file);
    }

    public ObjectInfo putObject(String bucketName, String path, String filename, File file) {
        return putObjectForKey(bucketName, formatPath(path) + filename, file);
    }

    public ObjectInfo putObjectForKey(String objectName, File file) {
        return putObjectForKey(ossProperties.getBucketName(), objectName, file);
    }

    public ObjectInfo putObjectForKey(String bucketName, String objectName, File file) {
        objectName = formatPath(objectName);
        PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(bucketName).key(objectName)
                .contentType(Util.getContentType(objectName))
                .build();
        UploadFileRequest uploadFileReq = UploadFileRequest.builder()
                .putObjectRequest(putReq).source(file).build();
        transferManager.uploadFile(uploadFileReq).completionFuture().join();
        return buildObjectInfo(objectName, new Date(), file.length());
    }

    // ----------------------------------------------------------------
    // 目录操作
    // ----------------------------------------------------------------

    public ObjectInfo mkdirs(String path) {
        return mkdirs(ossProperties.getBucketName(), path);
    }

    public ObjectInfo mkdirs(String bucketName, String path) {
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucketName).key(formatPath(path)).build();
        handleRequest(() -> client.putObject(req, AsyncRequestBody.empty()));
        return buildObjectInfo(path, new Date(), 0);
    }

    public void putFolder(String path, File folder) {
        putFolder(path, folder, true);
    }

    public void putFolder(String path, File folder, boolean isIncludeFolderName) {
        putFolder(ossProperties.getBucketName(), path, folder, isIncludeFolderName);
    }

    public void putFolder(String bucketName, String path, File folder, boolean isIncludeFolderName) {
        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException("目录不存在: " + folder.getPath());
        }
        path = formatPath(path);
        if (isIncludeFolderName) {
            path += folder.getName() + "/";
        }
        UploadDirectoryRequest req = UploadDirectoryRequest.builder()
                .source(Paths.get(folder.getAbsolutePath()))
                .s3Prefix(path).bucket(bucketName).build();
        transferManager.uploadDirectory(req).completionFuture().join();
    }

    // ----------------------------------------------------------------
    // 拷贝 / 移动
    // ----------------------------------------------------------------

    public void copyFile(String sourceKey, String destKey) {
        copyFile(ossProperties.getBucketName(), ossProperties.getBucketName(), sourceKey, destKey);
    }

    public void copyFile(String sourceBucket, String destBucket, String sourceKey, String destKey) {
        CopyObjectRequest req = CopyObjectRequest.builder()
                .sourceBucket(sourceBucket).sourceKey(formatPath(sourceKey))
                .destinationBucket(destBucket).destinationKey(formatPath(destKey))
                .build();
        handleRequest(() -> client.copyObject(req));
    }

    public void move(String sourceObjectName, String destinationDirectory) {
        move(ossProperties.getBucketName(), sourceObjectName, destinationDirectory);
    }

    public void move(String bucketName, String sourceObjectName, String destinationDirectory) {
        String filename = Util.getFilename(sourceObjectName);
        String destKey = Util.formatPath(destinationDirectory) + filename;
        copyFile(bucketName, bucketName, sourceObjectName, destKey);
        handleRequest(() -> client.deleteObject(x -> x.bucket(bucketName).key(formatPath(sourceObjectName)).build()));
    }

    // ----------------------------------------------------------------
    // 分片上传
    // ----------------------------------------------------------------

    public String initTask(ChunkTask chunkTask) {
        String objectName = formatPath(chunkTask.getPath()) + chunkTask.getFilename();
        CreateMultipartUploadResponse resp = client.createMultipartUpload(b -> b
                .bucket(ossProperties.getBucketName()).key(objectName)).join();
        return resp.uploadId();
    }

    public ChunkTarget chunk(Chunk chunk) {
        UploadPartRequest req = UploadPartRequest.builder()
                .bucket(ossProperties.getBucketName())
                .key(formatPath(chunk.getPath()) + chunk.getFilename())
                .uploadId(chunk.getUploadId())
                .partNumber(chunk.getChunkNumber())
                .contentLength(chunk.getFile().getSize())
                .build();
        try {
            ByteBuffer buf = ByteBuffer.wrap(chunk.getFile().getBytes());
            String etag = client.uploadPart(req, AsyncRequestBody.fromByteBuffer(buf)).join().eTag();
            ChunkTarget target = new ChunkTarget();
            target.setEtag(etag.replace("\"", ""));
            target.setPartNumber(chunk.getChunkNumber());
            return target;
        } catch (Exception e) {
            log.error("分片上传失败 file={} part={}", chunk.getFilename(), chunk.getChunkNumber(), e);
            throw OssException.uploadFailed(chunk.getFilename(), e);
        }
    }

    public ObjectInfo merge(ChunkMerge chunkMerge) {
        String objectName = formatPath(chunkMerge.getPath()) + chunkMerge.getFilename();
        List<CompletedPart> parts = chunkMerge.getChunkTargetList().stream()
                .map(p -> CompletedPart.builder().partNumber(p.getPartNumber()).eTag(p.getEtag()).build())
                .sorted(Comparator.comparingInt(CompletedPart::partNumber))
                .collect(Collectors.toList());

        client.completeMultipartUpload(b -> b
                .bucket(ossProperties.getBucketName()).key(objectName)
                .uploadId(chunkMerge.getUploadId())
                .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())).join();

        return ObjectInfo.builder()
                .uri(objectName).url(getDomain() + objectName)
                .name(Util.getFilename(objectName)).build();
    }

    public List<Part> listParts(String bucketName, String objectName, String uploadId) {
        ListPartsRequest req = ListPartsRequest.builder()
                .bucket(bucketName).key(objectName).uploadId(uploadId)
                .maxParts(Integer.MAX_VALUE).build();
        return client.listParts(req).join().parts();
    }

    // ----------------------------------------------------------------
    // 私有工具
    // ----------------------------------------------------------------

    /**
     * 将 InputStream 读入字节数组。
     * 改进：原代码使用 available()（不可靠），此处使用 ByteArrayOutputStream 完整读取。
     */
    private static byte[] toByteArray(InputStream stream) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = stream.read(buf)) != -1) {
                buffer.write(buf, 0, read);
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new OssException("STREAM_READ_ERROR", "Failed to read input stream", e);
        }
    }
}
