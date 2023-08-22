package com.wiblog.oss.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;
import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author panwm
 * @date 2023/8/20 21:40
 */
public class QueryOperations extends Operations {

    public QueryOperations(AmazonS3 amazonS3, OssProperties ossProperties) {
        super(ossProperties, amazonS3);
    }

    /**
     * 根据文件前置查询文件
     *
     * @param prefix 前缀
     */
    public List<ObjectInfo> getAllObjectsByPrefix(String prefix) {
        ObjectListing objectListing = amazonS3.listObjects(ossProperties.getBucketName(), prefix);
        return objectListing.getObjectSummaries().stream().map(e -> ObjectInfo.builder()
                .uri(e.getKey())
                .url(getDomain() + e.getKey())
                .name(Util.getFilename(e.getKey()))
                .uploadTime(e.getLastModified())
                .build()).collect(Collectors.toList());
    }

    /**
     * 根据文件前置查询文件
     *
     * @param prefix 前缀
     */
    public List<ObjectInfo> listObjectsV2(String prefix) {
        // 列出存储桶中的对象
        ListObjectsV2Request listObjectsRequest = new ListObjectsV2Request()
                .withBucketName(ossProperties.getBucketName()).withPrefix(prefix);

        ObjectListing objectListing = amazonS3.listObjects(ossProperties.getBucketName(), prefix);
        return objectListing.getObjectSummaries().stream().map(e -> ObjectInfo.builder()
                .uri(e.getKey())
                .url(getDomain() + e.getKey())
                .name(Util.getFilename(e.getKey()))
                .uploadTime(e.getLastModified())
                .build()).collect(Collectors.toList());
    }

    /**
     * 获取文件
     *
     * @param objectName 文件全路径
     * @return 二进制流
     */
    public ObjectInfo getObject(String objectName) {
        S3Object object = amazonS3.getObject(ossProperties.getBucketName(), objectName);
        return getObjectInfo(object);
    }

    private List<S3ObjectSummary> listObjects(String objectName) {
        // 列出存储桶中的对象
        ListObjectsV2Request request = new ListObjectsV2Request()
                .withBucketName(ossProperties.getBucketName()).withPrefix(objectName);

        List<S3ObjectSummary> objects = new ArrayList<>();
        ListObjectsV2Result response = null;

        do {
            response = amazonS3.listObjectsV2(request);
            objects.addAll(response.getObjectSummaries());

            if (response.isTruncated()) {
                String token = response.getNextContinuationToken();
                request.setContinuationToken(token);
            }
        } while (response.isTruncated());

        return objects;
    }

    /**
     * 获取目录结构
     *
     * @param objectName 文件全路径
     */
    public ObjectTreeNode getTreeList(String objectName) {
        List<S3ObjectSummary> objects = listObjects(objectName);
        return buildTree(objects, objectName);
    }

    public ObjectTreeNode buildTree(List<S3ObjectSummary> objectList, String objectName) {
        int i = objectName.lastIndexOf("/");
        String rootName = (i > 0) ? objectName.substring(i + 1) : objectName;
        ObjectTreeNode root = new ObjectTreeNode(rootName, objectName, getDomain() + objectName, null, "folder");

        for (S3ObjectSummary object : objectList) {
            if (object.getKey().startsWith(objectName + "/")) {
                String remainingPath = object.getKey().substring(objectName.length() + 1);
                addNode(root, remainingPath, object);
            }
        }

        return root;
    }

    private void addNode(ObjectTreeNode parentNode, String remainingPath, S3ObjectSummary object) {
        int slashIndex = remainingPath.indexOf('/');
        if (slashIndex == -1) { // 文件节点
            ObjectTreeNode fileNode = new ObjectTreeNode(remainingPath, object.getKey(), getDomain() + object.getKey(), object.getLastModified(), "file");
            parentNode.getChildren().add(fileNode);
        } else { // 文件夹节点
            String folderName = remainingPath.substring(0, slashIndex);
            String newRemainingPath = remainingPath.substring(slashIndex + 1);

            // 在当前节点的子节点中查找是否已存在同名文件夹节点
            ObjectTreeNode folderNode = findFolderNode(parentNode.getChildren(), folderName);
            if (folderNode == null) { // 若不存在，则创建新的文件夹节点
                folderNode = new ObjectTreeNode(folderName, parentNode.getUri() + "/" + folderName, getDomain() + parentNode.getUri() + "/" + folderName, null, "folder");
                parentNode.getChildren().add(folderNode);
            }

            addNode(folderNode, newRemainingPath, object);
        }
    }

    private ObjectTreeNode findFolderNode(List<ObjectTreeNode> nodes, String folderName) {
        for (ObjectTreeNode node : nodes) {
            if (node.getName().equals(folderName) && "folder".equals(node.getType())) {
                return node;
            }
        }
        return null;
    }

}
