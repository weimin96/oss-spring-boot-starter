package com.wiblog.oss.service;

import com.wiblog.oss.bean.BucketAccessInfo;
import com.wiblog.oss.bean.BucketDetailInfo;
import com.wiblog.oss.bean.BucketRewindResult;
import com.wiblog.oss.bean.CorsRuleInfo;
import com.wiblog.oss.bean.LifecycleRuleInfo;

import java.util.List;

/**
 * Bucket 管理能力端口。
 *
 * @author panwm
 */
public interface OssBucketService {
    void enableVersioning();

    void enableVersioning(String bucketName);

    void suspendVersioning();

    void suspendVersioning(String bucketName);

    String getVersioningStatus();

    String getVersioningStatus(String bucketName);

    BucketDetailInfo getBucketDetail(String bucketName);

    BucketAccessInfo getBucketAccess(String bucketName);

    BucketAccessInfo setBucketAccess(String bucketName, String acl);

    BucketRewindResult rewindBucket(String bucketName, String targetTime);

    List<LifecycleRuleInfo> getLifecycleRules();

    List<LifecycleRuleInfo> getLifecycleRules(String bucketName);

    void deleteLifecycleRules();

    void deleteLifecycleRules(String bucketName);

    void addExpirationRule(String ruleId, String prefix, int expirationDays);

    void addExpirationRule(String bucketName, String ruleId, String prefix, int expirationDays);

    List<CorsRuleInfo> getCorsRules();

    List<CorsRuleInfo> getCorsRules(String bucketName);

    void allowAllOriginsCors();

    void allowAllOriginsCors(String bucketName);

    void deleteCorsRules();

    void deleteCorsRules(String bucketName);

    String getBucketPolicy();

    String getBucketPolicy(String bucketName);

    void putBucketPolicy(String policyJson);

    void putBucketPolicy(String bucketName, String policyJson);

    void deleteBucketPolicy();

    void deleteBucketPolicy(String bucketName);

    void blockAllPublicAccess();

    void blockAllPublicAccess(String bucketName);

    void enableServerSideEncryption();

    void enableServerSideEncryption(String bucketName);
}
