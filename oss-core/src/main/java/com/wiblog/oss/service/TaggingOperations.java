package com.wiblog.oss.service;

import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.util.Util;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteBucketTaggingRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.GetBucketTaggingRequest;
import software.amazon.awssdk.services.s3.model.GetBucketTaggingResponse;
import software.amazon.awssdk.services.s3.model.GetObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.GetObjectTaggingResponse;
import software.amazon.awssdk.services.s3.model.PutBucketTaggingRequest;
import software.amazon.awssdk.services.s3.model.PutObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 标签操作类。
 *
 * @author panwm
 */
@Slf4j
public class TaggingOperations extends Operations {

    public TaggingOperations(OssProperties ossProperties, S3AsyncClient client,
                             S3TransferManager transferManager) {
        super(ossProperties, client, transferManager);
    }

    public Map<String, String> getObjectTags(String objectName) {
        return getObjectTags(ossProperties.getBucketName(), objectName);
    }

    public Map<String, String> getObjectTags(String bucketName, String objectName) {
        String normalizedObjectKey = normalizeObjectKey(objectName);
        GetObjectTaggingRequest req = GetObjectTaggingRequest.builder()
                .bucket(bucketName)
                .key(normalizedObjectKey)
                .build();
        GetObjectTaggingResponse resp = requireSuccessfulRequest(
                () -> client.getObjectTagging(req),
                "OBJECT_TAGS_QUERY_FAILED",
                "获取对象标签失败：" + normalizedObjectKey);
        return resp.tagSet().stream()
                .collect(Collectors.toMap(Tag::key, Tag::value));
    }

    public void setObjectTags(String objectName, Map<String, String> tags) {
        setObjectTags(ossProperties.getBucketName(), objectName, tags);
    }

    public void setObjectTags(String bucketName, String objectName, Map<String, String> tags) {
        String normalizedObjectKey = normalizeObjectKey(objectName);
        List<Tag> tagList = tags.entrySet().stream()
                .map(entry -> Tag.builder().key(entry.getKey()).value(entry.getValue()).build())
                .collect(Collectors.toList());

        PutObjectTaggingRequest req = PutObjectTaggingRequest.builder()
                .bucket(bucketName)
                .key(normalizedObjectKey)
                .tagging(Tagging.builder().tagSet(tagList).build())
                .build();
        requireSuccessfulRequest(() -> client.putObjectTagging(req),
                "OBJECT_TAGS_UPDATE_FAILED",
                "设置对象标签失败：" + normalizedObjectKey);
        log.debug("Set {} tags on object [{}]", tags.size(), normalizedObjectKey);
    }

    public void mergeObjectTags(String objectName, Map<String, String> tags) {
        mergeObjectTags(ossProperties.getBucketName(), objectName, tags);
    }

    public void mergeObjectTags(String bucketName, String objectName, Map<String, String> tags) {
        Map<String, String> mergedTags = new HashMap<String, String>(getObjectTags(bucketName, objectName));
        mergedTags.putAll(tags);
        setObjectTags(bucketName, objectName, mergedTags);
    }

    public void deleteObjectTags(String objectName) {
        deleteObjectTags(ossProperties.getBucketName(), objectName);
    }

    public void deleteObjectTags(String bucketName, String objectName) {
        String normalizedObjectKey = normalizeObjectKey(objectName);
        DeleteObjectTaggingRequest req = DeleteObjectTaggingRequest.builder()
                .bucket(bucketName)
                .key(normalizedObjectKey)
                .build();
        requireSuccessfulRequest(() -> client.deleteObjectTagging(req),
                "OBJECT_TAGS_DELETE_FAILED",
                "删除对象标签失败：" + normalizedObjectKey);
        log.debug("Deleted all tags on object [{}]", normalizedObjectKey);
    }

    public Map<String, String> getBucketTags() {
        return getBucketTags(ossProperties.getBucketName());
    }

    public Map<String, String> getBucketTags(String bucketName) {
        GetBucketTaggingRequest req = GetBucketTaggingRequest.builder()
                .bucket(bucketName)
                .build();
        GetBucketTaggingResponse resp = handleRequest(() -> client.getBucketTagging(req));
        if (resp == null) {
            return Collections.emptyMap();
        }
        return resp.tagSet().stream()
                .collect(Collectors.toMap(Tag::key, Tag::value));
    }

    public void setBucketTags(Map<String, String> tags) {
        setBucketTags(ossProperties.getBucketName(), tags);
    }

    public void setBucketTags(String bucketName, Map<String, String> tags) {
        List<Tag> tagList = tags.entrySet().stream()
                .map(entry -> Tag.builder().key(entry.getKey()).value(entry.getValue()).build())
                .collect(Collectors.toList());

        PutBucketTaggingRequest req = PutBucketTaggingRequest.builder()
                .bucket(bucketName)
                .tagging(Tagging.builder().tagSet(tagList).build())
                .build();
        requireSuccessfulRequest(() -> client.putBucketTagging(req),
                "BUCKET_TAGS_UPDATE_FAILED",
                "设置 Bucket 标签失败：" + bucketName);
        log.debug("Set {} tags on bucket [{}]", tags.size(), bucketName);
    }

    public void deleteBucketTags() {
        deleteBucketTags(ossProperties.getBucketName());
    }

    public void deleteBucketTags(String bucketName) {
        DeleteBucketTaggingRequest req = DeleteBucketTaggingRequest.builder()
                .bucket(bucketName)
                .build();
        requireSuccessfulRequest(() -> client.deleteBucketTagging(req),
                "BUCKET_TAGS_DELETE_FAILED",
                "删除 Bucket 标签失败：" + bucketName);
        log.debug("Deleted all tags on bucket [{}]", bucketName);
    }

    /**
     * 对象标签必须命中精确对象 key。
     *
     * 这里不能复用 formatPath 的“目录自动补斜杠”规则，
     * 否则无扩展名对象或目录风格对象名会被错误改写，导致写入和查询对不上同一个 key。
     */
    private String normalizeObjectKey(String objectKey) {
        if (Util.isBlank(objectKey)) {
            return "";
        }
        String normalizedKey = objectKey.trim().replace('\\', '/');
        while (normalizedKey.startsWith("/")) {
            normalizedKey = normalizedKey.substring(1);
        }
        return normalizedKey;
    }
}
