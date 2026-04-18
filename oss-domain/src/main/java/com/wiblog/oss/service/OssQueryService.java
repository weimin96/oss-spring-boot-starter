package com.wiblog.oss.service;

import com.wiblog.oss.bean.BucketInfo;
import com.wiblog.oss.bean.LazyDataList;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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

    String getContent(String objectName);

    String getContent(String bucketName, String objectName);

    InputStream getInputStream(String objectName);

    InputStream getInputStream(String bucketName, String objectName);

    InputStream getInputStream(String bucketName, String objectName, String range);

    File getFile(String objectName, String localFilePath);

    File getFile(String bucketName, String objectName, String localFilePath);

    void getFolder(String objectName, String localFilePath);

    void getFolder(String bucketName, String objectName, String localFilePath);

    void previewObject(OssPreviewContext context, String objectName, boolean download) throws IOException;

    ObjectTreeNode getTreeList(String path);

    ObjectTreeNode getTreeList(String bucketName, String path);

    ObjectTreeNode getTreeListByName(String path, String keyword);

    ObjectTreeNode getTreeListByName(String bucketName, String path, String keyword);
}
