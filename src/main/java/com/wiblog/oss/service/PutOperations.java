package com.wiblog.oss.service;

import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.bean.chunk.*;
import com.wiblog.oss.util.Util;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.BlockingInputStreamAsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 上传操作
 *
 * @author panwm
 * @since 2023/8/20 18:05
 */
@Slf4j
public class PutOperations extends Operations {

    public PutOperations(OssProperties ossProperties, S3AsyncClient client, S3TransferManager transferManager) {
        super(ossProperties, client, transferManager);
    }

    private boolean isExist(String bucketName) {
        HeadBucketRequest headBucketRequest = HeadBucketRequest.builder().bucket(bucketName).build();
        try {
            client.headBucket(headBucketRequest).join();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * 创建bucket
     *
     * @param bucketName bucket名称
     */
    public void createBucket(String bucketName) {
        if (!isExist(bucketName)) {
            CreateBucketRequest bucketRequest = CreateBucketRequest.builder()
                    .bucket(ossProperties.getBucketName())
                    .build();
            handleRequest(() -> client.createBucket(bucketRequest));
        }
    }

    /**
     * 上传文件
     *
     * @param path     路径
     * @param filename 文件名
     * @param in       文件流
     * @return 文件uri
     */
    public ObjectInfo putObject(String path, String filename, InputStream in) {
        return putObject(ossProperties.getBucketName(), path, filename, in);
    }

    /**
     * 上传文件
     *
     * @param bucketName 存储桶
     * @param path       路径
     * @param filename   文件名
     * @param in         文件流
     * @return 文件uri
     */
    public ObjectInfo putObject(String bucketName, String path, String filename, InputStream in) {
        return putObjectForKey(bucketName, formatPath(path) + filename, in);
    }


    /**
     * 上传文件
     *
     * @param path     存放路径
     * @param filename 文件名
     * @param file     文件
     * @return 文件uri
     */
    public ObjectInfo putObject(String path, String filename, File file) {
        return putObject(ossProperties.getBucketName(), path, filename, file);
    }

    /**
     * 上传文件
     *
     * @param bucketName 存储桶
     * @param path       存放路径
     * @param filename   文件名
     * @param file       文件
     * @return 文件uri
     */
    public ObjectInfo putObject(String bucketName, String path, String filename, File file) {
        return putObjectForKey(bucketName, formatPath(path) + filename, file);
    }

    /**
     * @param objectName 文件全路径
     * @param stream     文件流
     * @return 对象信息
     */
    public ObjectInfo putObjectForKey(String objectName, InputStream stream) {
        return putObjectForKey(ossProperties.getBucketName(), objectName, stream);
    }

    /**
     * @param objectName 文件全路径
     * @param file       文件
     * @return 对象信息
     */
    public ObjectInfo putObjectForKey(String objectName, File file) {
        return putObjectForKey(ossProperties.getBucketName(), objectName, file);
    }

    /**
     * @param bucketName 存储桶
     * @param objectName 文件全路径
     * @param file       文件
     * @return 对象信息
     */
    public ObjectInfo putObjectForKey(String bucketName, String objectName, File file) {
        objectName = formatPath(objectName);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder().bucket(bucketName)
                .key(objectName)
                .contentType(Util.getContentType(objectName))
                .build();
        UploadFileRequest uploadFileRequest = UploadFileRequest.builder().putObjectRequest(putObjectRequest)
                .source(file).build();
        // 构建上传请求对象
        FileUpload fileUpload = transferManager.uploadFile(uploadFileRequest);

        // 等待上传完成并获取上传结果
        fileUpload.completionFuture().join();
        return buildObjectInfo(objectName, new Date(), file.length());
    }

    /**
     * @param bucketName 存储桶
     * @param objectName 文件全路径
     * @param stream     文件流
     * @return 对象信息
     */
    public ObjectInfo putObjectForKey(String bucketName, String objectName, InputStream stream) {
        long fileSize = getFileSize(stream);
        objectName = formatPath(objectName);
        // 创建异步请求体（length如果为空会报错）
        BlockingInputStreamAsyncRequestBody body = AsyncRequestBody.forBlockingInputStream(fileSize);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder().bucket(bucketName)
                .key(objectName)
                .contentType(Util.getContentType(objectName))
                .build();
        UploadRequest uploadFileRequest = UploadRequest.builder().requestBody(body).putObjectRequest(putObjectRequest).build();

        // 使用 transferManager 进行上传
        Upload fileUpload = transferManager.upload(uploadFileRequest);

        // 将输入流写入请求体
        body.writeInputStream(stream);

        // 等待上传完成并获取上传结果
        fileUpload.completionFuture().join();
        return buildObjectInfo(objectName, new Date(), fileSize);
    }

    private int getFileSize(InputStream stream) {
        try {
            return stream.available();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 创建文件夹
     *
     * @param path 路径
     * @return ObjectInfo
     */
    public ObjectInfo mkdirs(String path) {
        return mkdirs(ossProperties.getBucketName(), path);
    }

    /**
     * 创建文件夹
     *
     * @param bucketName 桶名称
     * @param path       路径
     * @return ObjectInfo
     */
    public ObjectInfo mkdirs(String bucketName, String path) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(formatPath(path))
                .build();
        handleRequest(() -> client.putObject(putObjectRequest, AsyncRequestBody.empty()));
        return buildObjectInfo(path, new Date(), 0);
    }

    /**
     * 上传文件夹
     *
     * @param path   存放路径
     * @param folder 文件夹
     */
    public void putFolder(String path, File folder) {
        putFolder(path, folder, true);
    }

    /**
     * 上传文件夹
     *
     * @param path                存放路径
     * @param folder              文件夹
     * @param isIncludeFolderName 存放路径是否包含文件夹名称
     */
    public void putFolder(String path, File folder, boolean isIncludeFolderName) {
        putFolder(ossProperties.getBucketName(), path, folder, isIncludeFolderName);
    }

    /**
     * 上传文件夹
     *
     * @param bucketName          存储桶
     * @param path                存放路径
     * @param folder              文件夹
     * @param isIncludeFolderName 存放路径是否包含文件夹名称
     */
    public void putFolder(String bucketName, String path, File folder, boolean isIncludeFolderName) {
        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException("目录不存在: " + folder.getPath());
        }
        path = formatPath(path);
        if (isIncludeFolderName) {
            path += folder.getName() + "/";
        }
        UploadDirectoryRequest uploadDirectoryRequest = UploadDirectoryRequest.builder()
                .source(Paths.get(folder.getAbsolutePath()))
                .s3Prefix(path)
                .bucket(bucketName)
                .build();

        // 发起上传目录请求
        transferManager.uploadDirectory(uploadDirectoryRequest).completionFuture().join();
    }


    /**
     * 拷贝文件
     *
     * @param sourceDirectoryKey      源路径
     * @param destinationDirectoryKey 目标路径
     */
    public void copyFile(String sourceDirectoryKey, String destinationDirectoryKey) {
        // 拷贝文件
        copyFile(ossProperties.getBucketName(), ossProperties.getBucketName(), sourceDirectoryKey, destinationDirectoryKey);
    }

    /**
     * 数据汇聚 拷贝对象到目标存储桶
     *
     * @param sourceBucketName        源BucketName
     * @param destinationBucketName   目标BucketName
     * @param sourceDirectoryKey      源目录
     * @param destinationDirectoryKey 目标目录
     */
    public void copyFile(String sourceBucketName, String destinationBucketName, String sourceDirectoryKey, String destinationDirectoryKey) {
        CopyObjectRequest copyReq = CopyObjectRequest.builder()
                .sourceBucket(sourceBucketName)
                .sourceKey(formatPath(sourceDirectoryKey))
                .destinationBucket(destinationBucketName)
                .destinationKey(formatPath(destinationDirectoryKey))
                .build();
        handleRequest(() -> client.copyObject(copyReq));
    }

    /**
     * 初始化分片上传任务
     * @param chunkTask  分片任务
     * @return uploadId
     */
    public String initTask(ChunkTask chunkTask) {
        String objectName = formatPath(chunkTask.getPath()) + chunkTask.getFilename();
        // 初始化分片上传任务
        CreateMultipartUploadResponse createMultipartUploadResponse = client.createMultipartUpload(b -> b
                .bucket(ossProperties.getBucketName())
                .key(objectName)).join();
        return createMultipartUploadResponse.uploadId();
    }

    /**
     * 接收文件分片
     * @param chunk 分片
     * @return ChunkTarget
     */
    public ChunkTarget chunk(Chunk chunk) {
        ChunkTarget target = new ChunkTarget();
        // 上传
        UploadPartRequest uploadRequest = UploadPartRequest.builder()
                .bucket(ossProperties.getBucketName())
                .key(formatPath(chunk.getPath()) + chunk.getFilename())
                .uploadId(chunk.getUploadId())
                .partNumber(chunk.getChunkNumber())
                .contentLength(chunk.getFile().getSize())
                .build();

        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(chunk.getFile().getBytes());
            AsyncRequestBody body = AsyncRequestBody.fromByteBuffer(byteBuffer);
            String etag = client.uploadPart(uploadRequest, body).join().eTag();
            target.setEtag(etag.replace("\"", ""));
            target.setPartNumber(chunk.getChunkNumber());
        } catch (Exception e) {
            log.error("文件【{}】上传分片【{}】失败", chunk.getFilename(), chunk.getChunkNumber(), e);
            throw new RuntimeException("上传分片失败", e);
        }
        return target;
    }

    /**
     * 合并文件
     * @param chunkMerge 合并对象
     * @return ObjectInfo
     */
    public ObjectInfo merge(ChunkMerge chunkMerge) {
        String objectName = formatPath(chunkMerge.getPath()) + chunkMerge.getFilename();


        List<CompletedPart> completedParts = chunkMerge.getChunkTargetList().stream().map(part -> CompletedPart.builder()
                        .partNumber(part.getPartNumber())
                        .eTag(part.getEtag())
                        .build())
                .sorted(Comparator.comparingInt(CompletedPart::partNumber))
                .collect(Collectors.toList());

        client.completeMultipartUpload(b -> b
                .bucket(ossProperties.getBucketName())
                .key(objectName)
                .uploadId(chunkMerge.getUploadId())
                .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())).join();

        return ObjectInfo.builder()
                .uri(objectName)
                .url(getDomain() + objectName)
                .name(Util.getFilename(objectName))
                .build();
    }

    public List<Part> listParts(String bucketName, String objectName, String uploadId) {
        ListPartsRequest request = ListPartsRequest.builder()
                .bucket(bucketName)
                .key(objectName)
                .uploadId(uploadId)
                .maxParts(Integer.MAX_VALUE)
                .build();
        ListPartsResponse response = client.listParts(request).join();
        return response.parts();
    }

}
