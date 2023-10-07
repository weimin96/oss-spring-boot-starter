package com.wiblog.oss.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.util.IOUtils;
import com.amazonaws.util.StringUtils;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;
import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.util.Util;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLDecoder;
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
        return buildObjectInfo(object);
    }

    /**
     * 获取文件信息
     *
     * @param objectName 文件全路径
     * @return ObjectInfo对象信息
     */
    public ObjectInfo getObjectDetailInfo(String objectName) {
        ObjectInfo objectInfo = getObjectInfo(objectName);
        // 获取文件的访问权限
//        GetObjectAclRequest aclRequest = new GetObjectAclRequest(ossProperties.getBucketName(), objectName);
//        AccessControlList acl = amazonS3.getObjectAcl(aclRequest);
//        objectInfo.setAcl(acl);
        return objectInfo;
    }

    /**
     * 获取文件信息
     *
     * @param bucketName 存储桶
     * @param objectName 文件全路径
     * @return S3Object
     */
    public S3Object getS3Object(String bucketName, String objectName) {
        try {
            if (objectName.startsWith("/")) {
                objectName = objectName.substring(1);
            }
            return amazonS3.getObject(bucketName, objectName);
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == 404) {
                // 文件不存在，返回空值
                return null;
            } else {
                // 其他异常，继续抛出
                throw e;
            }
        }
    }

    /**
     * 获取文本内容
     *
     * @param objectName 文件全路径
     * @return InputStream 文件流
     */
    public String getContent(String objectName) {
        return getContent(ossProperties.getBucketName(), objectName);
    }

    /**
     * 获取文本内容
     *
     * @param bucketName 存储桶
     * @param objectName 文件全路径
     * @return InputStream 文件流
     */
    public String getContent(String bucketName, String objectName) {
        InputStream inputStream = getInputStream(bucketName, objectName);
        try {
            return IOUtils.toString(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取文件流
     *
     * @param objectName 文件全路径
     * @return InputStream 文件流
     */
    public InputStream getInputStream(String objectName) {
        return getInputStream(ossProperties.getBucketName(), objectName);
    }

    /**
     * 获取文件流
     *
     * @param bucketName 存储桶
     * @param objectName 文件全路径
     * @return InputStream 文件流
     */
    public InputStream getInputStream(String bucketName, String objectName) {
        S3Object s3Object = getS3Object(bucketName, objectName);
        return s3Object.getObjectContent().getDelegateStream();
    }

    /**
     * 下载文件
     *
     * @param objectName    文件全路径
     * @param localFilePath 存放位置
     * @return File
     */
    public File getFile(String objectName, String localFilePath) {
        return getFile(ossProperties.getBucketName(), objectName, localFilePath);
    }

    /**
     * 下载文件
     *
     * @param bucketName    存储桶
     * @param objectName    文件全路径
     * @param localFilePath 存放位置
     * @return File
     */
    public File getFile(String bucketName, String objectName, String localFilePath) {
        S3Object s3Object = getS3Object(bucketName, objectName);
        File outputFile = new File(localFilePath);
        if (!outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }
        String filename = Util.getFilename(objectName);
        Util.formatPath(localFilePath);
        if (!Util.checkIsFile(localFilePath)) {
            if (!outputFile.exists()) {
                outputFile.mkdirs();
            }
            localFilePath = Util.formatPath(localFilePath);
            outputFile = new File(localFilePath + filename);
        }

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = s3Object.getObjectContent().read(buffer)) != -1) {

                fos.write(buffer, 0, bytesRead);
            }
            s3Object.getObjectContent().close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return outputFile;
    }

    /**
     * 下载文件夹
     *
     * @param objectName    文件全路径
     * @param localFilePath 存放位置
     */
    public void getFolder(String objectName, String localFilePath) {
        getFolder(ossProperties.getBucketName(), objectName, localFilePath);
    }

    /**
     * 下载文件夹
     *
     * @param bucketName    存储桶
     * @param objectName    文件全路径
     * @param localFilePath 存放位置
     */
    public void getFolder(String bucketName, String objectName, String localFilePath) {
        List<S3ObjectSummary> s3ObjectSummaries = listObjectSummary(bucketName, objectName);
        if (!localFilePath.endsWith(File.pathSeparator)) {
            localFilePath += File.separator;
        }
        try {
            for (S3ObjectSummary objectSummary : s3ObjectSummaries) {
                S3Object s3Object = amazonS3.getObject(objectSummary.getBucketName(), objectSummary.getKey());
                S3ObjectInputStream inputStream = s3Object.getObjectContent();
                String filepath;
                String slash = "/".equals(File.separator) ? "/" : "\\\\";
                String key = objectSummary.getKey().replace(objectName + "/", "").replaceAll("/", slash);
                filepath = localFilePath + key;
                Util.copyInputStreamToFile(inputStream, filepath);

                s3Object.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 预览文件
     *
     * @param response   响应
     * @param objectName 文件全路径
     * @throws IOException io异常
     */
    public void previewObject(HttpServletResponse response, String objectName) throws IOException {
        objectName = URLDecoder.decode(objectName, "UTF-8");
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
        String filename = Util.getFilename(s3Object.getKey());
        response.setHeader("Content-Disposition", "inline; filename=\"" + filename + "\"");
        // 设置响应内容类型为
        response.setContentType(s3Object.getObjectMetadata().getContentType());

        // 关闭流
        outputStream.flush();
        outputStream.close();
        inputStream.close();
        s3Object.close();
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
        String rootName;
        if (StringUtils.isNullOrEmpty(objectName)) {
            rootName = "";
        } else {
            int i = objectName.lastIndexOf("/");
            rootName = (i > 0) ? objectName.substring(i + 1) : objectName;
        }

        ObjectTreeNode root = new ObjectTreeNode(rootName, objectName, getDomain() + objectName, null, "folder");

        for (S3ObjectSummary object : objectList) {
            if (object.getKey().startsWith(objectName + "/")) {
                String remainingPath = object.getKey().substring(objectName.length() + 1);
                addNode(root, remainingPath, object);
            } else if (StringUtils.isNullOrEmpty(objectName)) {
                addNode(root, object.getKey(), object);
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
                String uri = StringUtils.isNullOrEmpty(parentNode.getUri()) ? folderName : parentNode.getUri() + "/" + folderName;
                folderNode = new ObjectTreeNode(folderName, uri, getDomain() + uri, null, "folder");
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
