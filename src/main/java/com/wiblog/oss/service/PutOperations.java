package com.wiblog.oss.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.bean.chunk.Chunk;
import com.wiblog.oss.bean.chunk.ChunkProcess;
import com.wiblog.oss.util.Util;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 上传操作
 *
 * @author panwm
 * @since 2023/8/20 18:05
 */
@Slf4j
public class PutOperations extends Operations {

    /**
     * 分片进度
     */
    private static final Map<String, ChunkProcess> CHUNK_PROCESS_STORAGE = new ConcurrentHashMap<>();

    public PutOperations(AmazonS3 amazonS3, OssProperties ossProperties) {
        super(ossProperties, amazonS3);
    }

    /**
     * 创建bucket
     *
     * @param bucketName bucket名称
     */
    public void createBucket(String bucketName) {
        if (!amazonS3.doesBucketExistV2(bucketName)) {
            amazonS3.createBucket((bucketName));
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
    public ObjectInfo putObject(String path, String filename, InputStream in, String contentType) {
        path = Util.formatPath(path);
        return putObjectForKey(ossProperties.getBucketName(), path + filename, in, contentType);
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
        path = Util.formatPath(path);
        return putObjectForKey(bucketName, path + filename, in);
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
        path = Util.formatPath(path);
        return putObjectForKey(bucketName, path + filename, file);
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
        PutObjectRequest request = new PutObjectRequest(bucketName, objectName, file)
                .withCannedAcl(CannedAccessControlList.PublicRead);
        return putObject(request, objectName);
    }

    /**
     * @param bucketName 存储桶
     * @param objectName 文件全路径
     * @param stream     文件流
     * @return 对象信息
     */
    public ObjectInfo putObjectForKey(String bucketName, String objectName, InputStream stream) {
        return putObjectForKey(bucketName, objectName, stream, "application/octet-stream");
    }

    /**
     * @param bucketName  存储桶
     * @param objectName  文件全路径
     * @param stream      文件流
     * @param contentType 内容类型
     * @return 对象信息
     */
    public ObjectInfo putObjectForKey(String bucketName, String objectName, InputStream stream, String contentType) {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        try {
            objectMetadata.setContentLength(stream.available());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        objectMetadata.setContentType(contentType);

        PutObjectRequest request = new PutObjectRequest(bucketName, objectName, stream, objectMetadata)
                .withCannedAcl(CannedAccessControlList.PublicRead);

        return putObject(request, objectName);
    }

    private ObjectInfo putObject(PutObjectRequest request, String objectName) {
        // 执行文件上传
        try {
            amazonS3.putObject(request);
        } catch (Exception e) {
            log.error("上传失败", e);
        }
        return buildObjectInfo(objectName);
    }

    public ObjectInfo mkdirs(String path) {
        return mkdirs(ossProperties.getBucketName(), path);
    }

    public ObjectInfo mkdirs(String bucketName, String path) {
        path = Util.formatPath(path);
        PutObjectRequest request = new PutObjectRequest(bucketName, path, new ByteArrayInputStream(new byte[0]), null);
        return putObject(request, path);
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
            throw new IllegalArgumentException("Invalid folder path: " + folder.getPath());
        }
        path = Util.formatPath(path);
        if (isIncludeFolderName) {
            path += folder.getName() + "/";
        }

        try (Stream<Path> paths = Files.walk(Paths.get(folder.getPath()))) {
            String finalPath = path;
            paths.filter(Files::isRegularFile)
                    .forEach(filePath -> {
                        String key = finalPath + folder.toURI().relativize(filePath.toUri()).getPath();
                        putObjectForKey(bucketName, key, filePath.toFile());
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 拷贝文件
     *
     * @param sourceDirectoryKey      源路径
     * @param destinationDirectoryKey 目标路径
     */
    public void copyFile(String sourceDirectoryKey, String destinationDirectoryKey) {
        // 拷贝文件
        CopyObjectRequest copyRequest = new CopyObjectRequest(ossProperties.getBucketName(), sourceDirectoryKey, ossProperties.getBucketName(), destinationDirectoryKey);
        copyRequest.withCannedAccessControlList(CannedAccessControlList.PublicRead);
        amazonS3.copyObject(copyRequest);
    }

    /**
     * 数据汇聚 拷贝对象到目标存储桶
     *
     * @param sourceOssTemplate       源客户端
     * @param sourceBucketName        源BucketName
     * @param sourceDirectoryKey      源目录
     * @param destinationDirectoryKey 目标目录
     */
    public void transferObject(OssTemplate sourceOssTemplate, String sourceBucketName, String sourceDirectoryKey, String destinationDirectoryKey) {
        sourceDirectoryKey = Util.formatPath(sourceDirectoryKey);
        destinationDirectoryKey = Util.formatPath(destinationDirectoryKey);
        // 列出文件
        List<S3ObjectSummary> sourceObjects = sourceOssTemplate.query().listObjectSummary(sourceBucketName, sourceDirectoryKey);
        // 遍历并拷贝每个对象到目标存储桶
        try {
            for (S3ObjectSummary object : sourceObjects) {
                String sourceObjectKey = object.getKey();

                S3Object sourceObject = sourceOssTemplate.query().getS3Object(sourceBucketName, sourceObjectKey);
                String obsObjectKey = destinationDirectoryKey + sourceObjectKey.substring(sourceDirectoryKey.length());

                PutObjectRequest obsPutObjectRequest = new PutObjectRequest(ossProperties.getBucketName(), obsObjectKey, sourceObject.getObjectContent(), new ObjectMetadata());
                obsPutObjectRequest.withCannedAcl(CannedAccessControlList.PublicRead);
                amazonS3.putObject(obsPutObjectRequest);
            }
        } catch (Exception e) {
            log.error("上传文件异常", e);
        }

    }

    private String initTask(String objectName) {
        // 初始化分片上传任务
        InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest(ossProperties.getBucketName(), objectName)
                .withCannedACL(CannedAccessControlList.PublicRead);
        InitiateMultipartUploadResult initResponse = amazonS3.initiateMultipartUpload(initRequest);
        return initResponse.getUploadId();
    }

    /**
     * 接收文件分片
     *
     * @param chunk 文件分片
     */
    public void chunk(Chunk chunk) {
        String guid = chunk.getGuid();
        String objectKey = Util.formatPath(chunk.getPath()) + chunk.getFilename();
        ChunkProcess chunkProcess;
        String uploadId;


        if (CHUNK_PROCESS_STORAGE.containsKey(guid)) {
            chunkProcess = CHUNK_PROCESS_STORAGE.get(guid);
            uploadId = chunkProcess.getUploadId();
            AtomicBoolean isUploaded = new AtomicBoolean(false);
            Optional.ofNullable(chunkProcess.getChunkList()).ifPresent(chunkPartList ->
                    isUploaded.set(chunkPartList.stream().anyMatch(chunkPart -> chunkPart.getChunkNumber() == chunk.getChunkNumber())));
            if (isUploaded.get()) {
                log.info("文件【{}】分块【{}】已经上传，跳过", chunk.getFilename(), chunk.getChunkNumber());
                return;
            }
        } else {
            // 初始化
            uploadId = initTask(objectKey);
            chunkProcess = new ChunkProcess().setObjectKey(objectKey).setUploadId(uploadId);
            CHUNK_PROCESS_STORAGE.put(guid, chunkProcess);
        }

        List<ChunkProcess.ChunkPart> chunkList = chunkProcess.getChunkList();
        String chunkId = chunk(chunk, uploadId);
        chunkList.add(new ChunkProcess.ChunkPart(chunkId, chunk.getChunkNumber()));
        CHUNK_PROCESS_STORAGE.put(guid, chunkProcess.setChunkList(chunkList));
    }

    /**
     * 合并分片
     *
     * @param guid 文件唯一id
     * @return ObjectInfo文件信息
     */
    public ObjectInfo merge(String guid) {
        ChunkProcess chunkProcess = CHUNK_PROCESS_STORAGE.get(guid);
        ObjectInfo merge = merge(chunkProcess);
        CHUNK_PROCESS_STORAGE.remove(guid);
        return merge;
    }

    private String chunk(Chunk chunk, String uploadId) {
        // 上传
        UploadPartRequest uploadRequest = new UploadPartRequest()
                .withBucketName(ossProperties.getBucketName())
                .withKey(Util.formatPath(chunk.getPath()) + chunk.getFilename())
                .withUploadId(uploadId)
                .withPartNumber(chunk.getChunkNumber())
                .withPartSize(chunk.getFile().getSize());
        try (InputStream in = chunk.getFile().getInputStream()) {
            uploadRequest.withInputStream(in);
            UploadPartResult uploadResult = amazonS3.uploadPart(uploadRequest);
            return uploadResult.getETag();
        } catch (IOException e) {
            log.error("文件【{}】上传分片【{}】失败", chunk.getFilename(), chunk.getChunkNumber(), e);
            // 中止上传
            AbortMultipartUploadRequest abortRequest = new AbortMultipartUploadRequest(uploadRequest.getBucketName(), uploadRequest.getKey(), uploadId);
            amazonS3.abortMultipartUpload(abortRequest);
            // TODO 失败或取消删除文件
            throw new RuntimeException(e);
        }
    }


    private ObjectInfo merge(ChunkProcess chunkProcess) {
        List<PartETag> partETagList = chunkProcess.getChunkList()
                .stream()
                .map(chunkPart -> new PartETag(chunkPart.getChunkNumber(), chunkPart.getLocation()))
                .collect(Collectors.toList());
        CompleteMultipartUploadRequest compRequest = new CompleteMultipartUploadRequest(ossProperties.getBucketName(), chunkProcess.getObjectKey(),
                chunkProcess.getUploadId(), partETagList);
        amazonS3.completeMultipartUpload(compRequest);
        return ObjectInfo.builder()
                .uri(chunkProcess.getObjectKey())
                .url(getDomain() + chunkProcess.getObjectKey())
                .name(Util.getFilename(chunkProcess.getObjectKey()))
                .build();
    }

}
