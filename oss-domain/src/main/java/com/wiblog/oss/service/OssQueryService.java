package com.wiblog.oss.service;

import com.wiblog.oss.bean.BucketInfo;
import com.wiblog.oss.bean.LazyDataList;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;
import com.wiblog.oss.bean.ReadObjectRangeCommand;
import com.wiblog.oss.bean.StoredObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * 查询能力端口。
 *
 * @author panwm
 */
public interface OssQueryService {
    boolean testConnect();

    boolean testConnectForBucket(String bucketName);

    boolean testConnectForBucket();

    List<BucketInfo> getAllBuckets();

    List<ObjectInfo> listObjects(String path);

    List<ObjectInfo> listObjects(String bucketName, String path);

    LazyDataList<ObjectInfo> lazyList(String path, int maxKeys, String continuationToken);

    LazyDataList<ObjectInfo> lazyList(String bucketName, String path, int maxKeys, String continuationToken);

    List<ObjectTreeNode> listNextLevel(String path);

    List<ObjectTreeNode> listNextLevel(String bucketName, String path);

    List<ObjectTreeNode> getFolderTreeList(String path);

    List<ObjectTreeNode> getFolderTreeList(String bucketName, String path);

    List<ObjectInfo> listNextLevelFolder(String path);

    List<ObjectInfo> listNextLevelFolder(String bucketName, String path);

    boolean checkExist(String objectName);

    boolean checkExist(String bucketName, String objectName);

    ObjectInfo getObjectInfo(String objectName);

    ObjectInfo getObjectInfo(String bucketName, String objectName);

    StoredObject headObject(String bucket, String key);

    String getContent(String objectName);

    String getContent(String bucketName, String objectName);

    /**
     * 打开默认 Bucket 中对象的完整输入流。
     *
     * <p>调用方负责关闭返回的输入流，关闭流会释放底层 HTTP 连接。</p>
     */
    InputStream getInputStream(String objectName);

    /**
     * 打开指定 Bucket 中对象的完整输入流。
     *
     * <p>调用方负责关闭返回的输入流，关闭流会释放底层 HTTP 连接。</p>
     */
    InputStream getInputStream(String bucketName, String objectName);

    /**
     * 打开对象指定字节区间的输入流。
     *
     * <p>调用方负责关闭返回的输入流，关闭流会释放底层 HTTP 连接。</p>
     *
     * @param command 字节区间读取命令
     * @return 指定区间的对象输入流
     */
    default InputStream getInputStream(ReadObjectRangeCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("读取命令不能为空");
        }
        if (command.key() == null || command.key().trim().isEmpty()) {
            throw new IllegalArgumentException("对象 key 不能为空");
        }
        if (command.bucket() == null || command.bucket().trim().isEmpty()) {
            throw new IllegalArgumentException("兼容适配器要求显式指定 Bucket");
        }
        if (command.offset() < 0) {
            throw new IllegalArgumentException("读取偏移量不能小于 0");
        }
        if (command.length() <= 0) {
            throw new IllegalArgumentException("读取长度必须大于 0");
        }
        if (command.offset() > Long.MAX_VALUE - (command.length() - 1)) {
            throw new IllegalArgumentException("读取区间超出 long 范围");
        }
        long end = command.offset() + command.length() - 1;
        return getInputStream(command.bucket(), command.key(),
                "bytes=" + command.offset() + "-" + end);
    }

    /**
     * 使用原始 S3 Range 表达式打开对象输入流。
     *
     * @deprecated Java API 应使用 {@link #getInputStream(ReadObjectRangeCommand)}，
     * 原始 Range 字符串仅保留用于兼容已有调用。
     */
    @Deprecated
    InputStream getInputStream(String bucketName, String objectName, String range);

    File getFile(String objectName, String localFilePath);

    File getFile(String bucketName, String objectName, String localFilePath);

    void getFolder(String objectName, String localFilePath);

    void getFolder(String bucketName, String objectName, String localFilePath);

    /**
     * 按前缀列举默认 Bucket 下的真实对象，并流式写成 ZIP。
     *
     * <p>S3 没有真实文件夹概念，因此 path 表示 prefix。
     * 这里直接暴露 {@link OutputStream}，是为了让 Web 端点、自定义控制器和非 HTTP 场景都能复用同一条读链路。</p>
     */
    void writeFolderAsZip(String path, OutputStream outputStream) throws IOException;

    /**
     * 按前缀列举指定 Bucket 下的真实对象，并流式写成 ZIP。
     */
    void writeFolderAsZip(String bucketName, String path, OutputStream outputStream) throws IOException;

    void previewObject(OssPreviewContext context, String objectName, boolean download) throws IOException;

    ObjectTreeNode getTreeList(String path);

    ObjectTreeNode getTreeList(String bucketName, String path);

    ObjectTreeNode getTreeListByName(String path, String keyword);

    ObjectTreeNode getTreeListByName(String bucketName, String path, String keyword);
}
