package com.wiblog.oss.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.wiblog.oss.bean.Chunk;
import com.wiblog.oss.bean.ChunkProcess;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.OssProperties;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 上传操作
 *
 * @author panwm
 * @date 2023/8/20 18:05
 */
@Slf4j
public class PutOperations {

    private final OssProperties ossProperties;

    private final AmazonS3 amazonS3;

    public PutOperations(AmazonS3 amazonS3, OssProperties ossProperties) {
        this.amazonS3 = amazonS3;
        this.ossProperties = ossProperties;
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
        return getObjectResp(objectName);
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
     * 构造返回结构
     *
     * @param objectName objectName
     * @return ObjectResp
     */
    private ObjectInfo getObjectResp(String objectName) {
        URL url;
        try {
            url = new URL(ossProperties.getEndpoint());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        return ObjectInfo.builder()
                .uri(objectName)
                .url(url.getProtocol() + "://" + ossProperties.getBucketName() + "." + url.getHost() + "/" + objectName)
                .filename(Util.getFilename(objectName))
                .build();
    }


    // 分片进度
    private static final Map<String, ChunkProcess> CHUNK_PROCESS_STORAGE = new ConcurrentHashMap<>();

    // 文件列表
    private static final List<ObjectInfo> FILE_STORAGE = new CopyOnWriteArrayList<>();

    public String initTask(String filename) {
        // 初始化分片上传任务
        InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest(ossProperties.getBucketName(), filename);
        InitiateMultipartUploadResult initResponse = amazonS3.initiateMultipartUpload(initRequest);
        return initResponse.getUploadId();
    }

    public void chunk(Chunk chunk) {
        String filename = chunk.getFilename();
        boolean match = FILE_STORAGE.stream().anyMatch(ObjectInfo -> ObjectInfo.getFilename().equals(filename));
        if (match) {
            throw new RuntimeException("File [ " + filename + " ] already exist");
        }
        ChunkProcess chunkProcess;
        String uploadId;
        if (CHUNK_PROCESS_STORAGE.containsKey(filename)) {
            chunkProcess = CHUNK_PROCESS_STORAGE.get(filename);
            uploadId = chunkProcess.getUploadId();
            AtomicBoolean isUploaded = new AtomicBoolean(false);
            Optional.ofNullable(chunkProcess.getChunkList()).ifPresent(chunkPartList ->
                    isUploaded.set(chunkPartList.stream().anyMatch(chunkPart -> chunkPart.getChunkNumber() == chunk.getChunkNumber())));
            if (isUploaded.get()) {
                log.info("文件【{}】分块【{}】已经上传，跳过", chunk.getFilename(), chunk.getChunkNumber());
                return;
            }
        } else {
            uploadId = initTask(filename);
            chunkProcess = new ChunkProcess().setFilename(filename).setUploadId(uploadId);
            CHUNK_PROCESS_STORAGE.put(filename, chunkProcess);
        }

        List<ChunkProcess.ChunkPart> chunkList = chunkProcess.getChunkList();
        String chunkId = chunk(chunk, uploadId);
        chunkList.add(new ChunkProcess.ChunkPart(chunkId, chunk.getChunkNumber()));
        CHUNK_PROCESS_STORAGE.put(filename, chunkProcess.setChunkList(chunkList));
    }

    public void merge(String filename) {
        ChunkProcess chunkProcess = CHUNK_PROCESS_STORAGE.get(filename);
        merge(chunkProcess);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String currentTime = simpleDateFormat.format(new Date());
        FILE_STORAGE.add(ObjectInfo.builder().uploadTime(currentTime).filename(filename).build());
        CHUNK_PROCESS_STORAGE.remove(filename);
    }

    public String chunk(Chunk chunk, String uploadId) {
        try (InputStream in = chunk.getFile().getInputStream()) {
            // 上传
            UploadPartRequest uploadRequest = new UploadPartRequest()
                    .withBucketName(ossProperties.getBucketName())
                    .withKey(chunk.getFilename())
                    .withUploadId(uploadId)
                    .withInputStream(in)
                    .withPartNumber(chunk.getChunkNumber())
                    .withPartSize(chunk.getCurrentChunkSize());
            UploadPartResult uploadResult = amazonS3.uploadPart(uploadRequest);
            return uploadResult.getETag();
        } catch (IOException e) {
            log.error("文件【{}】上传分片【{}】失败", chunk.getFilename(), chunk.getChunkNumber(), e);
            throw new RuntimeException(e);
        }
    }

    public void merge(ChunkProcess chunkProcess) {
        List<PartETag> partETagList = chunkProcess.getChunkList()
                .stream()
                .map(chunkPart -> new PartETag(chunkPart.getChunkNumber(), chunkPart.getLocation()))
                .collect(Collectors.toList());
        CompleteMultipartUploadRequest compRequest = new CompleteMultipartUploadRequest(ossProperties.getBucketName(), chunkProcess.getFilename(),
                chunkProcess.getUploadId(), partETagList);
        amazonS3.completeMultipartUpload(compRequest);
    }

}
