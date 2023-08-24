package com.wiblog.oss.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;
import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.util.Util;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 查询操作类
 *
 * @author panwm
 * @since 2023/8/20 21:40
 */
public class QueryOperations extends Operations {

    public QueryOperations(AmazonS3 amazonS3, OssProperties ossProperties) {
        super(ossProperties, amazonS3);
    }

    /**
     * 获取全部bucket
     *
     * @return Bucket列表
     */
    public List<Bucket> getAllBuckets() {
        return amazonS3.listBuckets();
    }

    /**
     * 根据文件前置查询文件
     *
     * @param path 文件目录
     * @return Object信息列表
     */
    public List<ObjectInfo> listObjects(String path) {
        return listObjects(ossProperties.getBucketName(), path);
    }

    /**
     * 根据文件前置查询文件
     *
     * @param path       文件目录
     * @param bucketName 桶名称
     * @return Object信息列表
     */
    public List<ObjectInfo> listObjects(String bucketName, String path) {
        List<S3ObjectSummary> s3ObjectSummaries = listObjectSummary(bucketName, path);
        return s3ObjectSummaries.stream().map(e -> ObjectInfo.builder()
                .uri(e.getKey())
                .url(getDomain() + e.getKey())
                .name(Util.getFilename(e.getKey()))
                .uploadTime(e.getLastModified())
                .build()).collect(Collectors.toList());
    }

    /**
     * 根据文件前置查询文件列表
     *
     * @param path 文件目录
     * @return Object列表
     */
    public List<S3ObjectSummary> listObjectSummary(String path) {
        return listObjectSummary(ossProperties.getBucketName(), path);
    }

    /**
     * 根据文件前置查询文件列表
     *
     * @param path       文件目录
     * @param bucketName 桶名称
     * @return Object列表
     */
    public List<S3ObjectSummary> listObjectSummary(String bucketName, String path) {
        path = Util.formatPath(path);
        // 列出存储桶中的对象
        ListObjectsV2Request request = new ListObjectsV2Request()
                .withBucketName(bucketName).withPrefix(path);

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
     * 获取文件信息
     *
     * @param objectName 文件全路径
     * @return ObjectInfo对象信息
     */
    public ObjectInfo getObjectInfo(String objectName) {
        return getObjectInfo(ossProperties.getBucketName(), objectName);
    }

    /**
     * 获取文件信息
     *
     * @param bucketName 桶名称
     * @param objectName 文件全路径
     * @return ObjectInfo对象信息
     */
    public ObjectInfo getObjectInfo(String bucketName, String objectName) {
        S3Object object = getS3Object(bucketName, objectName);
        return getObjectInfo(object);
    }

    /**
     * 获取文件信息
     *
     * @param bucketName 存储桶
     * @param objectName 文件全路径
     * @return S3Object
     */
    public S3Object getS3Object(String bucketName, String objectName) {
        return amazonS3.getObject(bucketName, objectName);
    }

    /**
     * 预览文件
     *
     * @param response   响应
     * @param objectName 文件全路径
     * @throws IOException io异常
     */
    public void previewObject(HttpServletResponse response, String objectName) throws IOException {
        S3Object s3Object = amazonS3.getObject(ossProperties.getBucketName(), objectName);
        // 设置响应头信息
        response.setContentType(s3Object.getObjectMetadata().getContentType());
        response.setContentLength((int) s3Object.getObjectMetadata().getContentLength());

        // 获取文件输入流
        InputStream inputStream = s3Object.getObjectContent();

        // 将文件流写入响应输出流
        OutputStream outputStream = response.getOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }

        // 关闭流
        outputStream.flush();
        outputStream.close();
        inputStream.close();
    }

    /**
     * 获取目录结构
     *
     * @param path 目录
     * @return 树形结构
     */
    public ObjectTreeNode getTreeList(String path) {
        return getTreeList(ossProperties.getBucketName(), path);
    }

    /**
     * 获取目录结构
     *
     * @param bucketName 存储桶
     * @param path       目录
     * @return 树形结构
     */
    public ObjectTreeNode getTreeList(String bucketName, String path) {
        List<S3ObjectSummary> objects = listObjectSummary(bucketName, path);
        return buildTree(objects, path);
    }

    private ObjectTreeNode buildTree(List<S3ObjectSummary> objectList, String objectName) {
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
