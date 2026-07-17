package com.wiblog.oss.service;

import com.wiblog.oss.bean.CopyObjectCommand;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.PutObjectCommand;
import com.wiblog.oss.bean.StoredObject;
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

    StoredObject putObject(PutObjectCommand command);

    default ObjectInfo putObjectForKey(String objectName, InputStream stream) {
        return toObjectInfo(putObject(new PutObjectCommand(
                null, objectName, stream, null, null, null, null, null, false)));
    }

    default ObjectInfo putObjectForKey(String bucketName, String objectName, InputStream stream) {
        return toObjectInfo(putObject(new PutObjectCommand(
                bucketName, objectName, stream, null, null, null, null, null, false)));
    }

    ObjectInfo putObject(String path, String filename, File file);

    ObjectInfo putObject(String bucketName, String path, String filename, File file);

    ObjectInfo putObjectForKey(String objectName, File file);

    ObjectInfo putObjectForKey(String bucketName, String objectName, File file);

    ObjectInfo mkdirs(String path);

    ObjectInfo mkdirs(String bucketName, String path);

    void putFolder(String path, File folder);

    void putFolder(String path, File folder, boolean isIncludeFolderName);

    void putFolder(String bucketName, String path, File folder, boolean isIncludeFolderName);

    default void copyFile(String sourceKey, String destKey) {
        copyObject(new CopyObjectCommand(null, sourceKey, null, destKey));
    }

    default void copyFile(String sourceBucket, String destBucket, String sourceKey, String destKey) {
        copyObject(new CopyObjectCommand(sourceBucket, sourceKey, destBucket, destKey));
    }

    /**
     * 在对象存储服务端复制对象。
     *
     * <p>源对象不超过单次复制阈值时使用 CopyObject；超过阈值时自动切换为
     * multipart upload 与 UploadPartCopy。分片大小和并发度由客户端配置约束，
     * 源和目标必须由当前客户端访问；该接口不支持跨 endpoint 中转复制。
     * 命令指定 sourceVersionId 时复制固定历史版本，并允许恢复到相同 Bucket/key。
     * 复制请求成功但结果 HEAD 校验失败时会抛出明确的“已完成但未验证”异常。</p>
     *
     * @param command 对象复制命令
     * @return 复制完成后的目标对象信息
     */
    StoredObject copyObject(CopyObjectCommand command);

    /**
     * 在默认 Bucket 内安全移动对象。
     *
     * <p>最终删除源对象时使用源 ETag 条件；源在移动期间发生变化或存储服务不支持条件删除时，
     * 目标可能已经复制完成，但源对象会被保留并显式报错。</p>
     */
    void move(String sourceObjectName, String destinationDirectory);

    void move(String bucketName, String sourceObjectName, String destinationDirectory);

    String initTask(ChunkTask chunkTask);

    ChunkTarget chunk(ChunkUploadCommand chunk);

    /**
     * 合并手工上传的分片。
     *
     * <p>命令必须提供 expectedPartCount 和 expectedSize。实现会回查服务端真实分片，
     * 校验连续编号、ETag、分片数量与总大小后才提交合并。</p>
     */
    ObjectInfo merge(ChunkMerge chunkMerge);

    List<ChunkPartInfo> listParts(String bucketName, String objectName, String uploadId);

    static ObjectInfo toObjectInfo(StoredObject object) {
        String key = object.key();
        int separatorIndex = key.lastIndexOf('/');
        String name = separatorIndex >= 0 ? key.substring(separatorIndex + 1) : key;
        int extensionIndex = name.lastIndexOf('.');
        String extension = extensionIndex > 0 ? name.substring(extensionIndex + 1) : null;
        return ObjectInfo.builder()
                .name(name)
                .uri(key)
                .size(object.size())
                .ext(extension)
                .build();
    }
}
