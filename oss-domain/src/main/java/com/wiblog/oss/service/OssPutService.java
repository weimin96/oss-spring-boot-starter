package com.wiblog.oss.service;

import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.chunk.*;

import java.io.File;
import java.io.InputStream;
import java.util.List;

/**
 * 上传能力端口。
 *
 * @author panwm
 */
public interface OssPutService {
    void createBucket(String bucketName);

    ObjectInfo putObject(String path, String filename, InputStream in);

    ObjectInfo putObject(String bucketName, String path, String filename, InputStream in);

    ObjectInfo putObjectForKey(String objectName, InputStream stream);

    ObjectInfo putObjectForKey(String bucketName, String objectName, InputStream stream);

    ObjectInfo putObject(String path, String filename, File file);

    ObjectInfo putObject(String bucketName, String path, String filename, File file);

    ObjectInfo putObjectForKey(String objectName, File file);

    ObjectInfo putObjectForKey(String bucketName, String objectName, File file);

    ObjectInfo mkdirs(String path);

    ObjectInfo mkdirs(String bucketName, String path);

    void putFolder(String path, File folder);

    void putFolder(String path, File folder, boolean isIncludeFolderName);

    void putFolder(String bucketName, String path, File folder, boolean isIncludeFolderName);

    void copyFile(String sourceKey, String destKey);

    void copyFile(String sourceBucket, String destBucket, String sourceKey, String destKey);

    void move(String sourceObjectName, String destinationDirectory);

    void move(String bucketName, String sourceObjectName, String destinationDirectory);

    String initTask(ChunkTask chunkTask);

    ChunkTarget chunk(ChunkUploadCommand chunk);

    ObjectInfo merge(ChunkMerge chunkMerge);

    List<ChunkPartInfo> listParts(String bucketName, String objectName, String uploadId);
}
