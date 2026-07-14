package com.wiblog.oss.service;

import com.wiblog.oss.bean.BucketInfo;
import com.wiblog.oss.bean.LazyDataList;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;
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

    InputStream getInputStream(String objectName);

    InputStream getInputStream(String bucketName, String objectName);

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
