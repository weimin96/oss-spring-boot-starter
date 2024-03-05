package com.wiblog.oss.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.bean.chunk.Chunk;
import com.wiblog.oss.bean.chunk.ChunkProcess;
import com.wiblog.oss.util.Util;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
        return putObject(request, objectName, file.length());
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

        try {
            return putObject(request, objectName, stream.available());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ObjectInfo putObject(PutObjectRequest request, String objectName, long fileSize) {
        // 执行文件上传
        try {
            amazonS3.putObject(request);
        } catch (Exception e) {
            log.error("上传失败", e);
        }
        return buildObjectInfo(objectName, fileSize);
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
        path = Util.formatPath(path);
        PutObjectRequest request = new PutObjectRequest(bucketName, path, new ByteArrayInputStream(new byte[0]), null);
        return putObject(request, path, 0L);
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
        List<S3ObjectSummary> sourceObjects = sourceOssTemplate.query().listObjectSummary(sourceBucketName, sourceDirectoryKey, null);
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

    /**
     * 上传大文件
     * @param bucketName 存储桶
     * @param objectName 文件全路径
     * @param stream 文件流
     */
    public void uploadBigFile(String bucketName, String objectName, InputStream stream) {
        // 声明线程池
        ExecutorService exec = Executors.newFixedThreadPool(3);
        int size = 0;
        try {
            size = stream.available();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        int minPartSize = 10 * 1024 * 1024;
        // 得到总共的段数，和 分段后，每个段的开始上传的字节位置
        List<Long> positions = Collections.synchronizedList(new ArrayList<>());
        long filePosition = 0;
        while (filePosition < size) {
            positions.add(filePosition);
            filePosition += Math.min(minPartSize, (size - filePosition));
        }
        log.info("总大小：{}，分为{}段", size, positions.size());
        // 创建一个列表保存所有分传的 PartETag, 在分段完成后会用到
        List<PartETag> partETags = Collections.synchronizedList(new ArrayList<>());
        // 第一步，初始化，声明下面将有一个 Multipart Upload
        InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest(bucketName, objectName);
        InitiateMultipartUploadResult initResponse = amazonS3.initiateMultipartUpload(initRequest);
        log.info("开始上传");
        long begin = System.currentTimeMillis();
        try {
            // MultipartFile 转 File
            File toFile = new File(objectName);
            FileUtils.copyInputStreamToFile(stream, toFile);
            for (int i = 0; i < positions.size(); i++) {
                int finalI = i;
                int finalSize = size;
                exec.execute(() -> {
                    long time1 = System.currentTimeMillis();
                    UploadPartRequest uploadRequest = new UploadPartRequest()
                            .withBucketName(bucketName)
                            .withKey(objectName)
                            .withUploadId(initResponse.getUploadId())
                            .withPartNumber(finalI + 1)
                            .withFileOffset(positions.get(finalI))
                            .withFile(toFile)
                            .withPartSize(Math.min(minPartSize, (finalSize - positions.get(finalI))));
                    // 第二步，上传分段，并把当前段的 PartETag 放到列表中
                    partETags.add(amazonS3.uploadPart(uploadRequest).getPartETag());
                    long time2 = System.currentTimeMillis();
                    log.info("第{}段上传耗时：{}", finalI + 1, (time2 - time1));
                });
            }
            //任务结束关闭线程池
            exec.shutdown();
            //判断线程池是否结束，不加会直接结束方法
            while (true) {
                if (exec.isTerminated()) {
                    break;
                }
            }

            // 第三步，完成上传，合并分段
            CompleteMultipartUploadRequest compRequest = new CompleteMultipartUploadRequest(bucketName, objectName,
                    initResponse.getUploadId(), partETags);
            amazonS3.completeMultipartUpload(compRequest);
            //删除本地缓存文件
            toFile.delete();
        } catch (Exception e) {
            amazonS3.abortMultipartUpload(new AbortMultipartUploadRequest(bucketName, objectName, initResponse.getUploadId()));
            log.error("Failed to upload, " + e.getMessage());
        }
        long end = System.currentTimeMillis();
        log.info("总上传耗时：{}", (end - begin));
    }

}
