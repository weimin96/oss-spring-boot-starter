package com.wiblog.oss.service;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.MultipleFileUpload;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.wiblog.oss.bean.ObjectResp;
import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.util.Util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * 上传操作
 *
 * @author panwm
 * @date 2023/8/20 18:05
 */
public class PutOperations {

    private final OssProperties ossProperties;

    private final AmazonS3 amazonS3;

    public PutOperations(AmazonS3 amazonS3, OssProperties ossProperties) {
        this.amazonS3 = amazonS3;
        this.ossProperties = ossProperties;
    }

    /**
     * 上传文件
     *
     * @param path     路径
     * @param filename 文件名
     * @param in       文件流
     * @return 文件uri
     */
    public ObjectResp putObject(String path, String filename, InputStream in) {
        path = Util.formatPath(path);
        return putObject(path + filename, in);
    }

    /**
     * 上传文件
     *
     * @param path     路径
     * @param filename 文件名
     * @param file     文件
     * @return 文件uri
     */
    public ObjectResp putObject(String path, String filename, File file) {
        path = Util.formatPath(path);
        return putObject(path + filename, file);
    }


    private ObjectResp putObject(String objectName, File file) {
        PutObjectRequest request = new PutObjectRequest(ossProperties.getBucketName(), objectName, file)
                .withCannedAcl(CannedAccessControlList.PublicRead);
        return putObject(request, objectName);
    }

    private ObjectResp putObject(String objectName, InputStream stream) {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        try {
            objectMetadata.setContentLength(stream.available());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        objectMetadata.setContentType("application/octet-stream");

        PutObjectRequest request = new PutObjectRequest(ossProperties.getBucketName(), objectName, stream, objectMetadata)
                .withCannedAcl(CannedAccessControlList.PublicRead);

        return putObject(request, objectName);
    }

    private ObjectResp putObject(PutObjectRequest request, String objectName) {
        // 执行文件上传
        amazonS3.putObject(request);
        return getObjectResp(objectName);
    }

    /**
     * 上传文件夹
     * @param path 存放路径
     * @param folder 文件夹
     */
    public void uploadDir(String path, File folder) {
        uploadDir(path, folder, true);
    }

    /**
     * 上传文件夹
     * @param path 存放路径
     * @param folder 文件夹
     * @param isIncludeFolderName 存放路径是否包含文件夹名称
     */
    public void uploadDir(String path, File folder, boolean isIncludeFolderName) {

        if (!folder.exists() || !folder.isDirectory()) {
            System.out.println("Invalid folder path: " + folder.getPath());
            return;
        }
        path = Util.formatPath(path);
        if (isIncludeFolderName) {
            path += folder.getName() + "/";
        }

        try (Stream<Path> paths = Files.walk(Paths.get(folder.getPath()))) {
            String finalPath = path;
            paths.filter(Files::isRegularFile)
                    .forEach(filePath -> {
                        String key = finalPath + folder.toURI().relativize(filePath.toUri()).getPath();
                        putObject(key, filePath.toFile());
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 构造返回结构
     * @param objectName objectName
     * @return ObjectResp
     */
    private ObjectResp getObjectResp(String objectName) {
        URL url;
        try {
            url = new URL(ossProperties.getEndpoint());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        return ObjectResp.builder()
                .uri(objectName)
                .url(url.getProtocol() + "://" + ossProperties.getBucketName() + "." + url.getHost() + "/" + objectName)
                .filename(Util.getFilename(objectName))
                .build();
    }
}
