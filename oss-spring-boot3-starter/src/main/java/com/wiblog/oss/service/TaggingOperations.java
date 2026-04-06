package com.wiblog.oss.service;

import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.util.Util;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 标签操作类（对象级别 & Bucket 级别）。
 *
 * <p>S3 标签用于成本分配、生命周期规则过滤、跨账号权限策略等场景。
 * 每个对象最多支持 10 个标签（键值对），Bucket 最多 50 个。</p>
 *
 * @author panwm
 */
@Slf4j
public class TaggingOperations extends Operations {

    public TaggingOperations(OssProperties ossProperties, S3AsyncClient client,
                             S3TransferManager transferManager) {
        super(ossProperties, client, transferManager);
    }

    // ----------------------------------------------------------------
    // 对象标签
    // ----------------------------------------------------------------

    /**
     * 获取对象的所有标签。
     *
     * @param objectName 对象 key
     * @return 标签 Map，key 为标签键，value 为标签值
     */
    public Map<String, String> getObjectTags(String objectName) {
        return getObjectTags(ossProperties.getBucketName(), objectName);
    }

    public Map<String, String> getObjectTags(String bucketName, String objectName) {
        GetObjectTaggingRequest req = GetObjectTaggingRequest.builder()
                .bucket(bucketName)
                .key(Util.formatPath(objectName))
                .build();
        GetObjectTaggingResponse resp = handleRequest(() -> client.getObjectTagging(req));
        if (resp == null) {
            return Map.of();
        }
        return resp.tagSet().stream()
                .collect(Collectors.toMap(Tag::key, Tag::value));
    }

    /**
     * 设置对象标签（覆盖，不是追加）。
     *
     * @param objectName 对象 key
     * @param tags       标签 Map
     */
    public void setObjectTags(String objectName, Map<String, String> tags) {
        setObjectTags(ossProperties.getBucketName(), objectName, tags);
    }

    public void setObjectTags(String bucketName, String objectName, Map<String, String> tags) {
        List<Tag> tagList = tags.entrySet().stream()
                .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                .collect(Collectors.toList());

        PutObjectTaggingRequest req = PutObjectTaggingRequest.builder()
                .bucket(bucketName)
                .key(Util.formatPath(objectName))
                .tagging(Tagging.builder().tagSet(tagList).build())
                .build();
        handleRequest(() -> client.putObjectTagging(req));
        log.debug("Set {} tags on object [{}]", tags.size(), objectName);
    }

    /**
     * 追加/更新对象标签（已有标签保留，冲突键覆盖）。
     *
     * @param objectName 对象 key
     * @param tags       要追加/更新的标签
     */
    public void mergeObjectTags(String objectName, Map<String, String> tags) {
        mergeObjectTags(ossProperties.getBucketName(), objectName, tags);
    }

    public void mergeObjectTags(String bucketName, String objectName, Map<String, String> tags) {
        Map<String, String> existing = getObjectTags(bucketName, objectName);
        // 现有标签 + 新标签合并，新标签覆盖同 key 的旧值
        java.util.HashMap<String, String> merged = new java.util.HashMap<>(existing);
        merged.putAll(tags);
        setObjectTags(bucketName, objectName, merged);
    }

    /**
     * 删除对象上的所有标签。
     *
     * @param objectName 对象 key
     */
    public void deleteObjectTags(String objectName) {
        deleteObjectTags(ossProperties.getBucketName(), objectName);
    }

    public void deleteObjectTags(String bucketName, String objectName) {
        DeleteObjectTaggingRequest req = DeleteObjectTaggingRequest.builder()
                .bucket(bucketName)
                .key(Util.formatPath(objectName))
                .build();
        handleRequest(() -> client.deleteObjectTagging(req));
        log.debug("Deleted all tags on object [{}]", objectName);
    }

    // ----------------------------------------------------------------
    // Bucket 标签
    // ----------------------------------------------------------------

    /**
     * 获取 Bucket 的所有标签。
     */
    public Map<String, String> getBucketTags() {
        return getBucketTags(ossProperties.getBucketName());
    }

    public Map<String, String> getBucketTags(String bucketName) {
        GetBucketTaggingRequest req = GetBucketTaggingRequest.builder()
                .bucket(bucketName)
                .build();
        GetBucketTaggingResponse resp = handleRequest(() -> client.getBucketTagging(req));
        if (resp == null) {
            return Map.of();
        }
        return resp.tagSet().stream()
                .collect(Collectors.toMap(Tag::key, Tag::value));
    }

    /**
     * 设置 Bucket 标签（覆盖）。
     */
    public void setBucketTags(Map<String, String> tags) {
        setBucketTags(ossProperties.getBucketName(), tags);
    }

    public void setBucketTags(String bucketName, Map<String, String> tags) {
        List<Tag> tagList = tags.entrySet().stream()
                .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                .collect(Collectors.toList());

        PutBucketTaggingRequest req = PutBucketTaggingRequest.builder()
                .bucket(bucketName)
                .tagging(Tagging.builder().tagSet(tagList).build())
                .build();
        handleRequest(() -> client.putBucketTagging(req));
        log.debug("Set {} tags on bucket [{}]", tags.size(), bucketName);
    }

    /**
     * 删除 Bucket 的所有标签。
     */
    public void deleteBucketTags() {
        deleteBucketTags(ossProperties.getBucketName());
    }

    public void deleteBucketTags(String bucketName) {
        DeleteBucketTaggingRequest req = DeleteBucketTaggingRequest.builder()
                .bucket(bucketName)
                .build();
        handleRequest(() -> client.deleteBucketTagging(req));
        log.debug("Deleted all tags on bucket [{}]", bucketName);
    }
}