package com.wiblog.oss.service;

import com.wiblog.oss.bean.OssProperties;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.util.ArrayList;

/**
 * 移除操作
 *
 * @author panwm
 * @since 2023/8/20 21:42
 */
@Slf4j
public class DeleteOperations extends Operations {

    public DeleteOperations(OssProperties ossProperties, S3AsyncClient client, S3TransferManager transferManager) {
        super(ossProperties, client, transferManager);
    }

    /**
     * 删除文件
     *
     * @param objectName 文件全路径
     */
    public void removeObject(String objectName) {
        removeObject(ossProperties.getBucketName(), objectName);
    }

    /**
     * 删除文件
     *
     * @param bucketName bucket名称
     * @param objectName 文件全路径
     */
    public void removeObject(String bucketName, String objectName) {
        handleRequest(() -> client.deleteObject(x -> x.bucket(bucketName)
                .key(formatPath(objectName))
                .build()));
    }

    /**
     * 删除文件夹
     *
     * @param path 文件夹
     */
    public void removeFolder(String path) {
        removeFolder(ossProperties.getBucketName(), path);
    }

    /**
     * 删除文件夹
     *
     * @param bucketName bucket名称
     * @param path       文件夹
     */
    public void removeFolder(String bucketName, String path) {
        ListObjectsV2Request listObjectsRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(path)
                .maxKeys(20)
                .build();

        boolean done = false;
        while (!done) {
            ListObjectsV2Response listObjectsResponse = handleRequest(() -> client.listObjectsV2(listObjectsRequest));
            ArrayList<ObjectIdentifier> toDelete = new ArrayList<>();
            listObjectsResponse.contents().forEach(s3Object -> toDelete.add(ObjectIdentifier.builder()
                    .key(formatPath(s3Object.key()))
                    .build()));

            DeleteObjectsRequest deleteObjectsRequest = DeleteObjectsRequest.builder()
                    .bucket(bucketName)
                    .delete(Delete.builder().objects(toDelete).build())
                    .build();
            handleRequest(() -> client.deleteObjects(deleteObjectsRequest));

            if (listObjectsResponse.nextContinuationToken() == null) {
                done = true;
            }
        }
    }
}
