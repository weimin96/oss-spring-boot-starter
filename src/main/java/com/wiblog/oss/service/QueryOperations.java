package com.wiblog.oss.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.wiblog.oss.bean.OssProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * @author panwm
 * @date 2023/8/20 21:40
 */
public class QueryOperations {

    private final OssProperties ossProperties;

    private final AmazonS3 amazonS3;

    public QueryOperations(AmazonS3 amazonS3, OssProperties ossProperties) {
        this.ossProperties = ossProperties;
        this.amazonS3 = amazonS3;
    }

    /**
     * 根据文件前置查询文件
     * @param bucketName bucket名称
     * @param prefix 前缀
     * @see <a href=
     * "http://docs.aws.amazon.com/goto/WebAPI/s3-2006-03-01/ListObjects">AWS API
     * Documentation</a>
     */
    public List<S3ObjectSummary> getAllObjectsByPrefix(String bucketName, String prefix) {
        ObjectListing objectListing = amazonS3.listObjects(bucketName, prefix);
        return new ArrayList<>(objectListing.getObjectSummaries());
    }


    /**
     * 获取文件
     * @param objectName 文件全路径
     * @return 二进制流
     */
    public S3Object getObject(String objectName) {
        return amazonS3.getObject(ossProperties.getBucketName(), objectName);
    }


    /**
     * 获取文件信息
     * @param bucketName bucket名称
     * @param objectName 文件名称
     * @see <a href= "http://docs.aws.amazon.com/goto/WebAPI/s3-2006-03-01/GetObject">AWS
     * API Documentation</a>
     */
    public S3Object getObjectInfo(String bucketName, String objectName) {
        return amazonS3.getObject(bucketName, objectName);
    }
}
