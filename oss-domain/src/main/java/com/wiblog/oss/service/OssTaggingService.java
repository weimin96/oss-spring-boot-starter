package com.wiblog.oss.service;

import java.util.Map;

/**
 * 标签能力端口。
 *
 * @author panwm
 */
public interface OssTaggingService {
    Map<String, String> getObjectTags(String objectName);

    Map<String, String> getObjectTags(String bucketName, String objectName);

    void setObjectTags(String objectName, Map<String, String> tags);

    void setObjectTags(String bucketName, String objectName, Map<String, String> tags);

    void mergeObjectTags(String objectName, Map<String, String> tags);

    void mergeObjectTags(String bucketName, String objectName, Map<String, String> tags);

    void deleteObjectTags(String objectName);

    void deleteObjectTags(String bucketName, String objectName);

    Map<String, String> getBucketTags();

    Map<String, String> getBucketTags(String bucketName);

    void setBucketTags(Map<String, String> tags);

    void setBucketTags(String bucketName, Map<String, String> tags);

    void deleteBucketTags();

    void deleteBucketTags(String bucketName);
}
