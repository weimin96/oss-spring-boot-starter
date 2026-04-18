package com.wiblog.oss.service;

import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.chunk.ChunkPartInfo;
import com.wiblog.oss.bean.chunk.ChunkMerge;
import com.wiblog.oss.bean.chunk.ChunkTarget;
import com.wiblog.oss.bean.chunk.ChunkTask;
import com.wiblog.oss.bean.chunk.ChunkUploadCommand;
import com.wiblog.oss.config.OssClientOptions;
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
public class PutOperations extends Operations implements OssPutService {

    /**
     * 创建上传操作门面。
     *
     * @param ossProperties   OSS 配置
     * @param client          S3 异步客户端
     * @param transferManager 传输管理器
     */
    public PutOperations(OssClientOptions ossProperties, S3AsyncClient client, S3TransferManager transferManager) {
        super(ossProperties, client, transferManager);
    }

    // ----------------------------------------------------------------
    // Bucket 操作
    // ----------------------------------------------------------------

    /**
     * 创建一个 Bucket；若已存在则跳过。
     *
     * <p>这里采用”存在即幂等成功”的语义，
     * 目的是让启动阶段的自动建桶和业务侧显式建桶都可以安全重复调用。</p>
     *
     * @param bucketName 待创建的 Bucket 名称
     */
    @Override
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

    /**
     * 向默认 Bucket 上传一个输入流对象。
     *
     * @param path     目标目录
     * @param filename 文件名
     * @param in       文件输入流
     * @return 上传后的对象信息
     */
    @Override
    public ObjectInfo putObject(String path, String filename, InputStream in) {
        return putObject(ossProperties.getBucketName(), path, filename, in);
    }

    /**
     * 向指定 Bucket 上传一个输入流对象。
     *
     * @param bucketName Bucket 名称
     * @param path       目标目录
     * @param filename   文件名
     * @param in         文件输入流
     * @return 上传后的对象信息
     */
    @Override
    public ObjectInfo putObject(String bucketName, String path, String filename, InputStream in) {
        return putObjectForKey(bucketName, formatPath(path) + filename, in);
    }

    /**
     * 按完整对象 key 向默认 Bucket 上传输入流。
     *
     * @param objectName 完整对象 key
     * @param stream     文件输入流
     * @return 上传后的对象信息
     */
    @Override
    public ObjectInfo putObjectForKey(String objectName, InputStream stream) {
        return putObjectForKey(ossProperties.getBucketName(), objectName, stream);
    }

    /**
     * 上传 InputStream。
     * <p>
     * 改进：原代码使用 stream.available() 获取大小（不可靠），
     * 现改为先将流读入缓冲区，用精确字节数上传，确保 Content-Length 正确。
     *
     * @param bucketName Bucket 名称
     * @param objectName 完整对象 key
     * @param stream     文件输入流
     * @return 上传后的对象信息
     */
    @Override
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

    /**
     * 向默认 Bucket 上传本地文件。
     *
     * @param path     目标目录
     * @param filename 目标文件名
     * @param file     本地文件
     * @return 上传后的对象信息
     */
    @Override
    public ObjectInfo putObject(String path, String filename, File file) {
        return putObject(ossProperties.getBucketName(), path, filename, file);
    }

    /**
     * 向指定 Bucket 上传本地文件。
     *
     * @param bucketName Bucket 名称
     * @param path       目标目录
     * @param filename   目标文件名
     * @param file       本地文件
     * @return 上传后的对象信息
     */
    @Override
    public ObjectInfo putObject(String bucketName, String path, String filename, File file) {
        return putObjectForKey(bucketName, formatPath(path) + filename, file);
    }

    /**
     * 按完整对象 key 向默认 Bucket 上传本地文件。
     *
     * @param objectName 完整对象 key
     * @param file       本地文件
     * @return 上传后的对象信息
     */
    @Override
    public ObjectInfo putObjectForKey(String objectName, File file) {
        return putObjectForKey(ossProperties.getBucketName(), objectName, file);
    }

    /**
     * 按完整对象 key 向指定 Bucket 上传本地文件。
     *
     * @param bucketName Bucket 名称
     * @param objectName 完整对象 key
     * @param file       本地文件
     * @return 上传后的对象信息
     */
    @Override
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

    /**
     * 在默认 Bucket 下创建一个目录占位对象。
     *
     * @param path 目录前缀
     * @return 创建后的目录对象信息
     */
    @Override
    public ObjectInfo mkdirs(String path) {
        return mkdirs(ossProperties.getBucketName(), path);
    }

    /**
     * 在指定 Bucket 下创建一个目录占位对象。
     *
     * <p>对象存储本身没有真实目录，这里通过写入一个以 `/` 结尾的空对象来稳定表达目录存在。</p>
     *
     * @param bucketName Bucket 名称
     * @param path       目录前缀
     * @return 创建后的目录对象信息
     */
    @Override
    public ObjectInfo mkdirs(String bucketName, String path) {
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucketName).key(formatPath(path)).build();
        handleRequest(() -> client.putObject(req, AsyncRequestBody.empty()));
        return buildObjectInfo(path, new Date(), 0);
    }

    /**
     * 上传整个目录到默认 Bucket。
     *
     * @param path   目标目录前缀
     * @param folder 本地目录
     */
    @Override
    public void putFolder(String path, File folder) {
        putFolder(path, folder, true);
    }

    /**
     * 上传整个目录到默认 Bucket，并控制是否保留目录名。
     *
     * @param path                目标目录前缀
     * @param folder              本地目录
     * @param isIncludeFolderName 是否把本地目录名拼入目标前缀
     */
    @Override
    public void putFolder(String path, File folder, boolean isIncludeFolderName) {
        putFolder(ossProperties.getBucketName(), path, folder, isIncludeFolderName);
    }

    /**
     * 上传整个目录到指定 Bucket。
     *
     * <p>目录上传复用了 TransferManager 的目录传输能力，
     * 是为了在大量文件场景下让 SDK 负责并发调度，而不是在业务层自己循环上传。</p>
     *
     * @param bucketName          Bucket 名称
     * @param path                目标目录前缀
     * @param folder              本地目录
     * @param isIncludeFolderName 是否把本地目录名拼入目标前缀
     */
    @Override
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

    /**
     * 在默认 Bucket 内复制对象。
     *
     * @param sourceKey 源对象 key
     * @param destKey   目标对象 key
     */
    @Override
    public void copyFile(String sourceKey, String destKey) {
        copyFile(ossProperties.getBucketName(), ossProperties.getBucketName(), sourceKey, destKey);
    }

    /**
     * 在指定源/目标 Bucket 之间复制对象。
     *
     * @param sourceBucket 源 Bucket
     * @param destBucket   目标 Bucket
     * @param sourceKey    源对象 key
     * @param destKey      目标对象 key
     */
    @Override
    public void copyFile(String sourceBucket, String destBucket, String sourceKey, String destKey) {
        CopyObjectRequest req = CopyObjectRequest.builder()
                .sourceBucket(sourceBucket).sourceKey(formatPath(sourceKey))
                .destinationBucket(destBucket).destinationKey(formatPath(destKey))
                .build();
        handleRequest(() -> client.copyObject(req));
    }

    /**
     * 在默认 Bucket 内移动对象到目标目录。
     *
     * @param sourceObjectName     源对象 key
     * @param destinationDirectory 目标目录
     */
    @Override
    public void move(String sourceObjectName, String destinationDirectory) {
        move(ossProperties.getBucketName(), sourceObjectName, destinationDirectory);
    }

    /**
     * 在指定 Bucket 内移动对象到目标目录。
     *
     * <p>对象存储不支持真正的 rename，这里通过”复制到新位置再删除旧对象”来实现移动语义。</p>
     *
     * @param bucketName           Bucket 名称
     * @param sourceObjectName     源对象 key
     * @param destinationDirectory 目标目录
     */
    @Override
    public void move(String bucketName, String sourceObjectName, String destinationDirectory) {
        String filename = Util.getFilename(sourceObjectName);
        String destKey = Util.formatPath(destinationDirectory) + filename;
        copyFile(bucketName, bucketName, sourceObjectName, destKey);
        handleRequest(() -> client.deleteObject(x -> x.bucket(bucketName).key(formatPath(sourceObjectName)).build()));
    }

    // ----------------------------------------------------------------
    // 分片上传
    // ----------------------------------------------------------------

    /**
     * 初始化一个分片上传任务。
     *
     * @param chunkTask 分片任务定义
     * @return S3 分片上传任务 ID
     */
    @Override
    public String initTask(ChunkTask chunkTask) {
        String objectName = formatPath(chunkTask.getPath()) + chunkTask.getFilename();
        CreateMultipartUploadResponse resp = client.createMultipartUpload(b -> b
                .bucket(ossProperties.getBucketName()).key(objectName)).join();
        return resp.uploadId();
    }

    /**
     * 上传单个分片。
     *
     * @param chunk 分片内容和分片元数据
     * @return 分片号与 ETag 信息
     */
    @Override
    public ChunkTarget chunk(ChunkUploadCommand chunk) {
        UploadPartRequest req = UploadPartRequest.builder()
                .bucket(ossProperties.getBucketName())
                .key(formatPath(chunk.getPath()) + chunk.getFilename())
                .uploadId(chunk.getUploadId())
                .partNumber(chunk.getChunkNumber())
                .contentLength(chunk.getContentLength())
                .build();
        try {
            ByteBuffer buf = ByteBuffer.wrap(chunk.getFileBytes());
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

    /**
     * 合并已上传的所有分片。
     *
     * <p>合并完成后会立即回查对象头信息，
     * 这样返回值能带上最终文件大小，而不是只返回名称和 URL。</p>
     *
     * @param chunkMerge 分片合并请求
     * @return 合并后的对象信息
     */
    @Override
    public ObjectInfo merge(ChunkMerge chunkMerge) {
        String objectName = formatPath(chunkMerge.getPath()) + chunkMerge.getFilename();
        // 处理 null 或空列表的情况
        List<ChunkTarget> chunkList = chunkMerge.getChunkTargetList();
        if (chunkList == null || chunkList.isEmpty()) {
            throw new IllegalArgumentException("分片列表不能为空，请确保所有分片已上传完成");
        }
        List<CompletedPart> parts = chunkMerge.getChunkTargetList().stream()
                .map(p -> CompletedPart.builder().partNumber(p.getPartNumber()).eTag(p.getEtag()).build())
                .sorted(Comparator.comparingInt(CompletedPart::partNumber))
                .collect(Collectors.toList());

        client.completeMultipartUpload(b -> b
                .bucket(ossProperties.getBucketName()).key(objectName)
                .uploadId(chunkMerge.getUploadId())
                .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())).join();

        // 合并成功后立即回查对象元数据，确保把最终文件大小返回给调用方，
        // 避免前端拿到一个只有名称和 URL 的不完整结果。
        HeadObjectResponse response = handleRequest(() -> client.headObject(HeadObjectRequest.builder()
                .bucket(ossProperties.getBucketName())
                .key(objectName)
                .build()));
        return buildObjectInfo(objectName, response);
    }

    /**
     * 查询某个分片上传任务已上传的分片列表。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象 key
     * @param uploadId   分片上传任务 ID
     * @return 已上传分片列表
     */
    @Override
    public List<ChunkPartInfo> listParts(String bucketName, String objectName, String uploadId) {
        ListPartsRequest req = ListPartsRequest.builder()
                .bucket(bucketName).key(objectName).uploadId(uploadId)
                .maxParts(Integer.MAX_VALUE).build();
        return client.listParts(req).join().parts().stream()
                .map(part -> {
                    ChunkPartInfo info = new ChunkPartInfo();
                    info.setPartNumber(part.partNumber());
                    info.setEtag(part.eTag());
                    info.setSize(part.size());
                    return info;
                })
                .collect(Collectors.toList());
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


