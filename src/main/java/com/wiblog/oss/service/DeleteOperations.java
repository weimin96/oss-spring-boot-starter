package com.wiblog.oss.service;

import com.amazonaws.services.s3.AmazonS3;
import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.util.Util;

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
}
