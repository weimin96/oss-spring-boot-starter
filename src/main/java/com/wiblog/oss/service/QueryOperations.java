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

    /**
     * 获取目录结构
     *
     * @param objectName 文件全路径
     */
    public List<ObjectTreeNode> getTreeList(String objectName) {
        List<ObjectTreeNode> tree = new ArrayList<>();
        String rootKey = "";

        // 列出存储桶中的对象
        ListObjectsV2Request listObjectsRequest = new ListObjectsV2Request()
                .withBucketName(ossProperties.getBucketName()).withPrefix(objectName);

        ListObjectsV2Result result;
        do {

            result = amazonS3.listObjectsV2(listObjectsRequest);

            for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
                String key = objectSummary.getKey();
                String[] parts = key.split("/");

                // 将文件键（key）分解成单个路径部分，构建树形结构
                ObjectTreeNode currentNode = null;
                for (String part : parts) {
                    if (!existsInTree(tree, part)) {
                        ObjectTreeNode newNode = new ObjectTreeNode(part);
                        if (currentNode == null) {
                            currentNode = newNode;
                            rootKey = part;
                        } else {
                            currentNode.addChild(newNode);
                            currentNode = newNode;
                        }
                        tree.add(newNode);
                    } else {
                        currentNode = findInTree(tree, part);
                    }
                }
            }

            listObjectsRequest.setContinuationToken(result.getNextContinuationToken());
        } while (result.isTruncated());

        // 打印树形结构
        printTree(findInTree(tree, rootKey), "");
        return tree;
    }

    private static ObjectTreeNode findInTree(List<ObjectTreeNode> tree, String name) {
        for (ObjectTreeNode node : tree) {
            if (node.getName().equals(name)) {
                return node;
            }
        }
        return null;
    }

    private static boolean existsInTree(List<ObjectTreeNode> tree, String name) {
        for (ObjectTreeNode node : tree) {
            if (node.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static void printTree(ObjectTreeNode node, String indent) {
        System.out.println(indent + node.getName());
        for (ObjectTreeNode child : node.getChildren()) {
            printTree(child, indent + "  ");
        }
    }

}
