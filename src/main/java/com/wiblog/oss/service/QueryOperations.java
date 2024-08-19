package com.wiblog.oss.service;

import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;
import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.util.Util;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 查询操作类
 *
 * @author panwm
 * @since 2023/8/20 21:40
 */
@Slf4j
public class QueryOperations extends Operations {

    public QueryOperations(OssProperties ossProperties, S3AsyncClient client, S3TransferManager transferManager) {
        super(ossProperties, client, transferManager);
    }

    /**
     * 测试是否连接成功
     *
     * @return boolean
     */
    public boolean testConnect() {
        return testConnectForBucket();
    }

    /**
     * 判断桶是否存在
     *
     * @param bucketName 桶名称
     * @return boolean
     */
    public boolean testConnectForBucket(String bucketName) {
        try {
            HeadBucketRequest request = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            client.headBucket(request).join();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断桶是否存在
     *
     * @return boolean
     */
    public boolean testConnectForBucket() {
        return testConnectForBucket(ossProperties.getBucketName());
    }

    /**
     * 获取全部bucket
     *
     * @return Bucket列表
     */
    public List<Bucket> getAllBuckets() {
        ListBucketsResponse response = client.listBuckets().join();
        return response.buckets();
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
        List<S3Object> s3ObjectSummaries = listObject(bucketName, path, null);
        return s3ObjectSummaries.stream().map(e -> ObjectInfo.builder()
                .uri(e.key())
                .url(getDomain() + e.key())
                .name(Util.getFilename(e.key()))
                .uploadTime(Date.from(e.lastModified()))
                .build()).collect(Collectors.toList());
    }

    /**
     * 根据文件前置查询文件列表
     *
     * @param bucketName 桶名称
     * @param path       文件目录
     * @return Object列表
     */
    public List<S3Object> listObject(String bucketName, String path) {
        return listObject(ossProperties.getBucketName(), path, null);
    }

    /**
     * 根据文件前置查询文件列表
     *
     * @param path 文件目录
     * @return Object列表
     */
    public List<S3Object> listObject(String path) {
        return listObject(ossProperties.getBucketName(), path, null);
    }

    /**
     * 根据文件前置查询文件列表
     *
     * @param path       文件目录
     * @param bucketName 桶名称
     * @param keyword    关键字
     * @return Object列表
     */
    public List<S3Object> listObject(String bucketName, String path, String keyword) {
        path = Util.formatPath(path);
        // 列出存储桶中的对象
        ListObjectsV2Request request = ListObjectsV2Request
                .builder()
                .bucket(bucketName)
                .prefix(path)
                .build();

        ListObjectsV2Response response = client.listObjectsV2(request).join();
        List<S3Object> collect;
        if (Util.isBlank(keyword)) {
            collect = response.contents();
        } else {
            collect = response.contents().stream().filter(e -> e.key().contains(keyword)).collect(Collectors.toList());
        }
        return collect;
    }

    /**
     * 获取下一层级目录树
     *
     * @param path 路径
     * @return List
     */
    public List<ObjectTreeNode> listNextLevel(String path) {
        return listNextLevel(ossProperties.getBucketName(), path);
    }

    /**
     * 获取下一层级目录树
     *
     * @param bucketName 桶名称
     * @param path       路径
     * @return List
     */
    public List<ObjectTreeNode> listNextLevel(String bucketName, String path) {
        path = Util.formatPath(path);
        // 列出存储桶中的对象
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(path)
                .delimiter("/")
                .build();

        ListObjectsV2Response response = client.listObjectsV2(request).join();
        List<S3Object> objects = response.contents();
        List<CommonPrefix> commonPrefixes = response.commonPrefixes();
        List<ObjectTreeNode> folders = commonPrefixes.stream().map(e -> buildTreeNode(e.prefix())).collect(Collectors.toList());
        List<ObjectTreeNode> files = objects.stream().filter(e -> e.size() > 0).map(this::buildTreeNode).collect(Collectors.toList());
        List<ObjectTreeNode> resultList = new ArrayList<>(folders);
        resultList.addAll(files);
        return resultList;
    }

    /**
     * 校验文件是否存在
     *
     * @param bucketName 桶名称
     * @param objectName 文件全路径
     * @return ObjectInfo对象信息
     */
    public boolean checkExist(String bucketName, String objectName) {
        HeadObjectRequest request = HeadObjectRequest.builder().bucket(bucketName).key(objectName).build();
        try {
            client.headObject(request).join();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * 校验文件是否存在
     *
     * @param objectName 文件全路径
     * @return ObjectInfo对象信息
     */
    public boolean checkExist(String objectName) {
        // 判断对象（Object）是否存在。
        return checkExist(ossProperties.getBucketName(), objectName);
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
        HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(objectName)
                .build();
        HeadObjectResponse response = handleRequest(() -> client.headObject(headObjectRequest));
        return buildObjectInfo(objectName, response);
    }

    /**
     * 获取文本内容
     *
     * @param objectName 文件全路径
     * @return String 文本
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
        try {
            return client.getObject(getObjectRequest(bucketName, objectName), AsyncResponseTransformer.toBytes()).thenApply(responseBytes -> {
                // 将 ByteBuffer 转换为字符串
                ByteBuffer buffer = responseBytes.asByteBuffer();
                return StandardCharsets.UTF_8.decode(buffer).toString();
            }).join();
        } catch (NoSuchKeyException e) {
            log.error("获取文件失败，文件不存在-【{}】", objectName);
        }
        return null;
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
        return handleRequest(() -> client.getObject(getObjectRequest(bucketName, objectName), AsyncResponseTransformer.toBytes()).thenApply(responseBytes -> {
            ByteBuffer buffer = responseBytes.asByteBuffer();
            byte[] bytesArray = new byte[buffer.remaining()];
            buffer.get(bytesArray);
            return new ByteArrayInputStream(bytesArray);
        }));
    }

    /**
     * 获取文件流
     *
     * @param bucketName 存储桶
     * @param objectName 文件全路径
     * @param range      分段
     * @return InputStream 文件流
     */
    public InputStream getInputStream(String bucketName, String objectName, String range) {
        GetObjectRequest request = GetObjectRequest.builder().bucket(bucketName).key(Util.formatPath(objectName)).range(range).build();
        return handleRequest(() -> client.getObject(request, AsyncResponseTransformer.toBytes()).thenApply(responseBytes -> {
            ByteBuffer buffer = responseBytes.asByteBuffer();
            byte[] bytesArray = new byte[buffer.remaining()];
            buffer.get(bytesArray);
            return new ByteArrayInputStream(bytesArray);
        }));
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
        File outputFile = new File(localFilePath);
        if (!outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }
        String filename = Util.getFilename(objectName);
        if (!Util.checkIsFile(localFilePath)) {
            if (!outputFile.exists()) {
                outputFile.mkdirs();
            }
            localFilePath = Util.formatPath(localFilePath);
            outputFile = new File(localFilePath + filename);
        }
        File finalOutputFile = outputFile;
        handleRequest(() -> client.getObject(getObjectRequest(bucketName, objectName), AsyncResponseTransformer.toFile(finalOutputFile)));
        return outputFile;
    }

    private GetObjectRequest getObjectRequest(String bucketName, String objectName) {
        return GetObjectRequest.builder().bucket(bucketName).key(Util.formatPath(objectName)).build();
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
        List<S3Object> s3Objects = listObject(bucketName, objectName, null);
        if (!localFilePath.endsWith(File.pathSeparator)) {
            localFilePath += File.separator;
        }
        for (S3Object s3Object : s3Objects) {
            String filepath;
            String slash = "/".equals(File.separator) ? "/" : "\\\\";
            String key = s3Object.key().replace(objectName + "/", "").replaceAll("/", slash);
            filepath = localFilePath + key;
            this.getFile(bucketName, s3Object.key(), filepath);
        }
    }

    /**
     * 预览文件
     *
     * @param request    请求
     * @param response   响应
     * @param objectName 文件全路径
     * @throws IOException io异常
     */
    public void previewObject(HttpServletRequest request, HttpServletResponse response, String objectName) throws IOException {
        if (Util.isBlank(objectName)) {
            return;
        }
        if (objectName.contains("%")) {
            objectName = URLDecoder.decode(objectName, "UTF-8");
        }

        try {
            // 设置响应头信息
            String fileName = Util.getFilename(objectName);
            String encodedFileName = java.net.URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");
            ObjectInfo objectInfo = getObjectInfo(objectName);
            long fileSize = objectInfo.getSize();

            response.setContentType(Util.getContentType(objectName));
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName);
            response.setHeader("Accept-Ranges", "bytes");
            String rangeHeader = request.getHeader("Range");
            if (rangeHeader == null) {
                // 完整下载
                try (InputStream inputStream = getInputStream(objectName);
                     OutputStream outputStream = response.getOutputStream()) {
                    response.setContentLengthLong(fileSize);
                    byte[] buffer = new byte[2 * 1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
            } else {
                // 分段下载
                long start;
                long end;
                String[] range = rangeHeader.split("=")[1].split("-");
                if (range.length == 1) {
                    start = Long.parseLong(range[0]);
                    end = fileSize - 1;
                } else {
                    start = Long.parseLong(range[0]);
                    end = Long.parseLong(range[1]);
                }
                long contentLength = end - start + 1;
                // 返回头里存放每次读取的开始和结束字节
                response.setHeader("Content-Length", String.valueOf(contentLength));
                response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
                try (InputStream inputStream = getInputStream(ossProperties.getBucketName(), objectName, rangeHeader);
                     OutputStream outputStream = response.getOutputStream()) {
                    // 跳到第start字节
//                    inputStream.skip(start);
                    byte[] buffer = new byte[2 * 1024];
                    int bytesRead;
                    long bytesWritten = 0;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        if (bytesWritten + bytesRead > contentLength) {
                            outputStream.write(buffer, 0, (int) (contentLength - bytesWritten));
                            break;
                        } else {
                            outputStream.write(buffer, 0, bytesRead);
                            bytesWritten += bytesRead;
                        }
                    }
                }
            }

        } catch (NoSuchKeyException e) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setHeader("content-type", "text/html;charset=utf-8");
            // 文件不存在
            response.getWriter().println("<html><head><title>404 Not Found</title></head><body><h1>404 Not Found</h1></body></html>");
        } catch (IOException e) {
            if ("Broken pipe".equals(e.getMessage())) {
                return;
            }
            throw new IOException(e);
        }
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
        List<S3Object> objects = listObject(bucketName, path);
        return buildTree(objects, path);
    }

    /**
     * 获取目录结构
     *
     * @param bucketName 存储桶
     * @param path       目录
     * @param keyword    关键字
     * @return 树形结构
     */
    public ObjectTreeNode getTreeListByName(String bucketName, String path, String keyword) {
        List<S3Object> objects = listObject(bucketName, path, keyword);
        return buildTree(objects, path);
    }


    /**
     * 获取目录结构
     *
     * @param path    目录
     * @param keyword 关键字
     * @return 树形结构
     */
    public ObjectTreeNode getTreeListByName(String path, String keyword) {
        return getTreeListByName(ossProperties.getBucketName(), path, keyword);
    }

    private ObjectTreeNode buildTree(List<S3Object> objectList, String objectName) {
        String rootName;
        if (Util.isBlank(objectName)) {
            rootName = "";
        } else {
            int i = objectName.lastIndexOf("/");
            rootName = (i > 0) ? objectName.substring(i + 1) : objectName;
        }

        ObjectTreeNode root = new ObjectTreeNode(rootName, objectName, getDomain() + objectName, null, "folder", 0, null);

        for (S3Object object : objectList) {
            if (object.key().startsWith(objectName + "/")) {
                String remainingPath = object.key().substring(objectName.length() + 1);
                addNode(root, remainingPath, object);
            } else if (!Util.isBlank(objectName)) {
                addNode(root, object.key(), object);
            }
        }

        return root;
    }

    private void addNode(ObjectTreeNode parentNode, String remainingPath, S3Object object) {
        int slashIndex = remainingPath.indexOf('/');
        if (slashIndex == -1) { // 文件节点
            if (Util.isBlank(remainingPath)) {
                return;
            }
            ObjectTreeNode fileNode = new ObjectTreeNode(remainingPath, object.key(), getDomain() + object.key(),
                    Date.from(object.lastModified()), "file", object.size(), Util.getExtension(object.key()));
            parentNode.addChild(fileNode);
        } else { // 文件夹节点
            String folderName = remainingPath.substring(0, slashIndex);
            String newRemainingPath = remainingPath.substring(slashIndex + 1);

            // 在当前节点的子节点中查找是否已存在同名文件夹节点
            ObjectTreeNode folderNode = findFolderNode(parentNode.getChildren(), folderName);
            if (folderNode == null) { // 若不存在，则创建新的文件夹节点
                String uri = Util.isBlank(parentNode.getUri()) ? folderName : parentNode.getUri() + "/" + folderName;
                folderNode = new ObjectTreeNode(folderName, uri, getDomain() + uri, null, "folder", 0, null);
                parentNode.addChild(folderNode);
            }

            addNode(folderNode, newRemainingPath, object);
        }
    }

    private ObjectTreeNode findFolderNode(List<ObjectTreeNode> nodes, String folderName) {
        if (nodes == null) {
            return null;
        }
        for (ObjectTreeNode node : nodes) {
            if (node.getName().equals(folderName) && "folder".equals(node.getType())) {
                return node;
            }
        }
        return null;
    }
}
