package com.wiblog.oss.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.bean.chunk.Chunk;
import com.wiblog.oss.bean.chunk.ChunkProcess;
import com.wiblog.oss.util.Util;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
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
     * 上传文件
     *
     * @param path     路径
     * @param filename 文件名
     * @param in       文件流
     * @return 文件uri
     */
    public ObjectInfo putObject(String path, String filename, InputStream in) {
        path = Util.formatPath(path);
        return putObject(path + filename, in);
    }

    /**
     * 上传文件
     *
     * @param path     路径
     * @param filename 文件名
     * @param file     文件
     * @return 文件uri
     */
    public ObjectInfo putObject(String path, String filename, File file) {
        path = Util.formatPath(path);
        return putObject(path + filename, file);
    }


    private ObjectInfo putObject(String objectName, File file) {
        PutObjectRequest request = new PutObjectRequest(ossProperties.getBucketName(), objectName, file)
                .withCannedAcl(CannedAccessControlList.PublicRead);
        return putObject(request, objectName);
    }

    private ObjectInfo putObject(String objectName, InputStream stream) {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        try {
            objectMetadata.setContentLength(stream.available());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        objectMetadata.setContentType("application/octet-stream");

        PutObjectRequest request = new PutObjectRequest(ossProperties.getBucketName(), objectName, stream, objectMetadata)
                .withCannedAcl(CannedAccessControlList.PublicRead);

        return putObject(request, objectName);
    }

    private ObjectInfo putObject(PutObjectRequest request, String objectName) {
        // 执行文件上传
        amazonS3.putObject(request);
        return getObjectInfo(objectName);
    }

    /**
     * 上传文件夹
     *
     * @param path   存放路径
     * @param folder 文件夹
     */
    public void uploadDir(String path, File folder) {
        uploadDir(path, folder, true);
    }

    /**
     * 上传文件夹
     *
     * @param path                存放路径
     * @param folder              文件夹
     * @param isIncludeFolderName 存放路径是否包含文件夹名称
     */
    public void uploadDir(String path, File folder, boolean isIncludeFolderName) {

        if (!folder.exists() || !folder.isDirectory()) {
            System.out.println("Invalid folder path: " + folder.getPath());
            return;
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
                        putObject(key, filePath.toFile());
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 数据汇聚 拷贝对象到目标存储桶
     * @param sourceOssTemplate 源客户端
     * @param sourceBucketName 源BucketName
     * @param sourceDirectoryKey 源目录
     * @param destinationDirectoryKey 目标目录
     */
    public void transferObject(OssTemplate sourceOssTemplate, String sourceBucketName, String sourceDirectoryKey, String destinationDirectoryKey) {
        sourceDirectoryKey = Util.formatPath(sourceDirectoryKey);
        destinationDirectoryKey = Util.formatPath(destinationDirectoryKey);
        // 列出文件
        List<S3ObjectSummary> sourceObjects = sourceOssTemplate.query().listObjects(sourceBucketName, sourceDirectoryKey);
        // 遍历并拷贝每个对象到目标存储桶
        for (S3ObjectSummary object : sourceObjects) {
            String sourceObjectKey = object.getKey();

            S3Object sourceObject = sourceOssTemplate.query().getObject(sourceBucketName, sourceObjectKey);
            String obsObjectKey = destinationDirectoryKey + sourceObjectKey.substring(sourceDirectoryKey.length());

            PutObjectRequest obsPutObjectRequest = new PutObjectRequest(ossProperties.getBucketName(), obsObjectKey, sourceObject.getObjectContent(), new ObjectMetadata());
            obsPutObjectRequest.withCannedAcl(CannedAccessControlList.PublicRead);
            amazonS3.putObject(obsPutObjectRequest);
        }
    }

    private String initTask(String objectName) {
        // 初始化分片上传任务
        InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest(ossProperties.getBucketName(), objectName)
                .withCannedACL(CannedAccessControlList.PublicRead);
        InitiateMultipartUploadResult initResponse = amazonS3.initiateMultipartUpload(initRequest);
        return initResponse.getUploadId();
    }

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

    public ObjectInfo merge(String guid) {
        ChunkProcess chunkProcess = CHUNK_PROCESS_STORAGE.get(guid);
        ObjectInfo merge = merge(chunkProcess);
        CHUNK_PROCESS_STORAGE.remove(guid);
        return merge;
    }

    private String chunk(Chunk chunk, String uploadId) {
        try (InputStream in = chunk.getFile().getInputStream()) {
            // 上传
            UploadPartRequest uploadRequest = new UploadPartRequest()
                    .withBucketName(ossProperties.getBucketName())
                    .withKey(Util.formatPath(chunk.getPath()) + chunk.getFilename())
                    .withUploadId(uploadId)
                    .withInputStream(in)
                    .withPartNumber(chunk.getChunkNumber())
                    .withPartSize(chunk.getFile().getSize());
            UploadPartResult uploadResult = amazonS3.uploadPart(uploadRequest);
            return uploadResult.getETag();
        } catch (IOException e) {
            log.error("文件【{}】上传分片【{}】失败", chunk.getFilename(), chunk.getChunkNumber(), e);
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
        URL url;
        try {
            url = new URL(ossProperties.getEndpoint());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return ObjectInfo.builder()
                .uri(chunkProcess.getObjectKey())
                .url(url.getProtocol() + "://" + ossProperties.getBucketName() + "." + url.getHost() + "/" + chunkProcess.getObjectKey())
                .name(Util.getFilename(chunkProcess.getObjectKey()))
                .build();

    }

}
