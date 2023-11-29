package com.wiblog.oss.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 移除操作
 * @author panwm
 * @since 2023/8/20 21:42
 */
public class DeleteOperations extends Operations {

    public DeleteOperations(AmazonS3 amazonS3, OssProperties ossProperties) {
        super(ossProperties, amazonS3);
    }

    /**
     * 删除文件
     * @param bucketName bucket名称
     * @param objectName 文件全路径
     */
    public void removeObject(String bucketName, String objectName) {
        amazonS3.deleteObject(bucketName, objectName);
    }

    /**
     * 删除文件
     * @param objectName 文件全路径
     */
    public void removeObject(String objectName) {
        removeObject(ossProperties.getBucketName(), objectName);
    }

    /**
     * 删除文件夹
     * @param path 文件夹
     */
    public void removeFolder(String path) {
        removeFolder(ossProperties.getBucketName(), path);
    }

    /**
     * 删除文件夹
     * @param bucketName bucket名称
     * @param path 文件夹
     */
    public void removeFolder(String bucketName, String path) {
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

        List<DeleteObjectsRequest.KeyVersion> keys = objects.stream().map(e -> new DeleteObjectsRequest.KeyVersion(e.getKey()))
                .collect(Collectors.toList());
        DeleteObjectsRequest deleteObjectsRequest = new DeleteObjectsRequest(bucketName).withKeys(keys);
        amazonS3.deleteObjects(deleteObjectsRequest);
    }
}
