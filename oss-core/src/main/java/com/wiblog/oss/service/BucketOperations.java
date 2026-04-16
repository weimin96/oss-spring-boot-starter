package com.wiblog.oss.service;

import com.wiblog.oss.bean.CorsRuleInfo;
import com.wiblog.oss.bean.LifecycleRuleInfo;
import com.wiblog.oss.bean.OssProperties;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.BucketLifecycleConfiguration;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.CORSConfiguration;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.DeleteBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketLifecycleRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.ExpirationStatus;
import software.amazon.awssdk.services.s3.model.GetBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.GetBucketCorsResponse;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionRequest;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionResponse;
import software.amazon.awssdk.services.s3.model.GetBucketLifecycleConfigurationRequest;
import software.amazon.awssdk.services.s3.model.GetBucketLifecycleConfigurationResponse;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyResponse;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningResponse;
import software.amazon.awssdk.services.s3.model.GetPublicAccessBlockRequest;
import software.amazon.awssdk.services.s3.model.GetPublicAccessBlockResponse;
import software.amazon.awssdk.services.s3.model.LifecycleExpiration;
import software.amazon.awssdk.services.s3.model.LifecycleRule;
import software.amazon.awssdk.services.s3.model.LifecycleRuleFilter;
import software.amazon.awssdk.services.s3.model.PublicAccessBlockConfiguration;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.PutBucketEncryptionRequest;
import software.amazon.awssdk.services.s3.model.PutBucketLifecycleConfigurationRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.PutPublicAccessBlockRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionByDefault;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionConfiguration;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionRule;
import software.amazon.awssdk.services.s3.model.VersioningConfiguration;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Bucket 级别操作。
 *
 * @author panwm
 */
@Slf4j
public class BucketOperations extends Operations {

    public BucketOperations(OssProperties ossProperties, S3AsyncClient client,
                            S3TransferManager transferManager) {
        super(ossProperties, client, transferManager);
    }

    public void enableVersioning() {
        enableVersioning(ossProperties.getBucketName());
    }

    public void enableVersioning(String bucketName) {
        setVersioningStatus(bucketName, BucketVersioningStatus.ENABLED);
    }

    public void suspendVersioning() {
        suspendVersioning(ossProperties.getBucketName());
    }

    public void suspendVersioning(String bucketName) {
        setVersioningStatus(bucketName, BucketVersioningStatus.SUSPENDED);
    }

    public String getVersioningStatus() {
        return getVersioningStatus(ossProperties.getBucketName());
    }

    public String getVersioningStatus(String bucketName) {
        GetBucketVersioningResponse resp = handleRequest(() ->
                client.getBucketVersioning(GetBucketVersioningRequest.builder().bucket(bucketName).build()));
        if (resp == null || resp.status() == null) {
            return null;
        }
        return resp.status().toString();
    }

    public void putLifecycleRules(List<LifecycleRule> rules) {
        putLifecycleRules(ossProperties.getBucketName(), rules);
    }

    public void putLifecycleRules(String bucketName, List<LifecycleRule> rules) {
        PutBucketLifecycleConfigurationRequest req = PutBucketLifecycleConfigurationRequest.builder()
                .bucket(bucketName)
                .lifecycleConfiguration(BucketLifecycleConfiguration.builder().rules(rules).build())
                .build();
        requireSuccessfulRequest(() -> client.putBucketLifecycleConfiguration(req),
                "BUCKET_LIFECYCLE_UPDATE_FAILED",
                "设置 Bucket 生命周期规则失败：" + bucketName);
        log.info("Set {} lifecycle rules on bucket [{}]", rules.size(), bucketName);
    }

    public List<LifecycleRuleInfo> getLifecycleRules() {
        return getLifecycleRules(ossProperties.getBucketName());
    }

    public List<LifecycleRuleInfo> getLifecycleRules(String bucketName) {
        return listLifecycleRuleModels(bucketName).stream()
                .map(this::toLifecycleRuleInfo)
                .collect(Collectors.toList());
    }

    public void deleteLifecycleRules() {
        deleteLifecycleRules(ossProperties.getBucketName());
    }

    public void deleteLifecycleRules(String bucketName) {
        requireSuccessfulRequest(() -> client.deleteBucketLifecycle(
                        DeleteBucketLifecycleRequest.builder().bucket(bucketName).build()),
                "BUCKET_LIFECYCLE_DELETE_FAILED",
                "删除 Bucket 生命周期规则失败：" + bucketName);
        log.info("Deleted lifecycle rules on bucket [{}]", bucketName);
    }

    public void addExpirationRule(String ruleId, String prefix, int expirationDays) {
        addExpirationRule(ossProperties.getBucketName(), ruleId, prefix, expirationDays);
    }

    public void addExpirationRule(String bucketName, String ruleId, String prefix, int expirationDays) {
        LifecycleRule rule = LifecycleRule.builder()
                .id(ruleId)
                .status(ExpirationStatus.ENABLED)
                .filter(LifecycleRuleFilter.builder().prefix(prefix).build())
                .expiration(LifecycleExpiration.builder().days(expirationDays).build())
                .build();

        List<LifecycleRule> existing = new ArrayList<LifecycleRule>(listLifecycleRuleModels(bucketName));
        existing.removeIf(r -> ruleId.equals(r.id()));
        existing.add(rule);
        putLifecycleRules(bucketName, existing);
        log.info("Added expiration rule [{}] on bucket [{}]: {} days, prefix='{}'",
                ruleId, bucketName, expirationDays, prefix);
    }

    public void putCorsRules(List<CORSRule> corsRules) {
        putCorsRules(ossProperties.getBucketName(), corsRules);
    }

    public void putCorsRules(String bucketName, List<CORSRule> corsRules) {
        PutBucketCorsRequest req = PutBucketCorsRequest.builder()
                .bucket(bucketName)
                .corsConfiguration(CORSConfiguration.builder().corsRules(corsRules).build())
                .build();
        requireSuccessfulRequest(() -> client.putBucketCors(req),
                "BUCKET_CORS_UPDATE_FAILED",
                "设置 Bucket CORS 规则失败：" + bucketName);
        log.info("Set CORS rules on bucket [{}]", bucketName);
    }

    public List<CorsRuleInfo> getCorsRules() {
        return getCorsRules(ossProperties.getBucketName());
    }

    public List<CorsRuleInfo> getCorsRules(String bucketName) {
        return listCorsRuleModels(bucketName).stream()
                .map(this::toCorsRuleInfo)
                .collect(Collectors.toList());
    }

    public void allowAllOriginsCors() {
        allowAllOriginsCors(ossProperties.getBucketName());
    }

    public void allowAllOriginsCors(String bucketName) {
        CORSRule rule = CORSRule.builder()
                .allowedOrigins("*")
                .allowedMethods("GET", "PUT", "POST", "DELETE", "HEAD")
                .allowedHeaders("*")
                .exposeHeaders("ETag", "x-amz-request-id")
                .maxAgeSeconds(3600)
                .build();
        putCorsRules(bucketName, Collections.singletonList(rule));
    }

    public void deleteCorsRules() {
        deleteCorsRules(ossProperties.getBucketName());
    }

    public void deleteCorsRules(String bucketName) {
        requireSuccessfulRequest(() -> client.deleteBucketCors(
                        DeleteBucketCorsRequest.builder().bucket(bucketName).build()),
                "BUCKET_CORS_DELETE_FAILED",
                "删除 Bucket CORS 规则失败：" + bucketName);
    }

    public String getBucketPolicy() {
        return getBucketPolicy(ossProperties.getBucketName());
    }

    public String getBucketPolicy(String bucketName) {
        GetBucketPolicyResponse resp = handleRequest(() ->
                client.getBucketPolicy(GetBucketPolicyRequest.builder().bucket(bucketName).build()));
        return resp == null ? null : resp.policy();
    }

    public void putBucketPolicy(String policyJson) {
        putBucketPolicy(ossProperties.getBucketName(), policyJson);
    }

    public void putBucketPolicy(String bucketName, String policyJson) {
        handleRequest(() -> client.putBucketPolicy(
                PutBucketPolicyRequest.builder().bucket(bucketName).policy(policyJson).build()));
        log.info("Updated policy on bucket [{}]", bucketName);
    }

    public void deleteBucketPolicy() {
        deleteBucketPolicy(ossProperties.getBucketName());
    }

    public void deleteBucketPolicy(String bucketName) {
        handleRequest(() -> client.deleteBucketPolicy(
                DeleteBucketPolicyRequest.builder().bucket(bucketName).build()));
        log.info("Deleted policy on bucket [{}]", bucketName);
    }

    public void blockAllPublicAccess() {
        blockAllPublicAccess(ossProperties.getBucketName());
    }

    public void blockAllPublicAccess(String bucketName) {
        PublicAccessBlockConfiguration config = PublicAccessBlockConfiguration.builder()
                .blockPublicAcls(true)
                .ignorePublicAcls(true)
                .blockPublicPolicy(true)
                .restrictPublicBuckets(true)
                .build();
        handleRequest(() -> client.putPublicAccessBlock(
                PutPublicAccessBlockRequest.builder()
                        .bucket(bucketName)
                        .publicAccessBlockConfiguration(config)
                        .build()));
        log.info("Blocked all public access on bucket [{}]", bucketName);
    }

    public PublicAccessBlockConfiguration getPublicAccessBlock() {
        return getPublicAccessBlock(ossProperties.getBucketName());
    }

    public PublicAccessBlockConfiguration getPublicAccessBlock(String bucketName) {
        GetPublicAccessBlockResponse resp = handleRequest(() ->
                client.getPublicAccessBlock(
                        GetPublicAccessBlockRequest.builder().bucket(bucketName).build()));
        return resp == null ? null : resp.publicAccessBlockConfiguration();
    }

    public void enableServerSideEncryption() {
        enableServerSideEncryption(ossProperties.getBucketName());
    }

    public void enableServerSideEncryption(String bucketName) {
        ServerSideEncryptionRule rule = ServerSideEncryptionRule.builder()
                .applyServerSideEncryptionByDefault(
                        ServerSideEncryptionByDefault.builder()
                                .sseAlgorithm(ServerSideEncryption.AES256)
                                .build())
                .bucketKeyEnabled(true)
                .build();

        handleRequest(() -> client.putBucketEncryption(
                PutBucketEncryptionRequest.builder()
                        .bucket(bucketName)
                        .serverSideEncryptionConfiguration(
                                ServerSideEncryptionConfiguration.builder().rules(rule).build())
                        .build()));
        log.info("Enabled SSE-S3 encryption on bucket [{}]", bucketName);
    }

    public ServerSideEncryptionConfiguration getEncryptionConfiguration() {
        return getEncryptionConfiguration(ossProperties.getBucketName());
    }

    public ServerSideEncryptionConfiguration getEncryptionConfiguration(String bucketName) {
        GetBucketEncryptionResponse resp = handleRequest(() ->
                client.getBucketEncryption(
                        GetBucketEncryptionRequest.builder().bucket(bucketName).build()));
        return resp == null ? null : resp.serverSideEncryptionConfiguration();
    }

    private void setVersioningStatus(String bucketName, BucketVersioningStatus status) {
        PutBucketVersioningRequest req = PutBucketVersioningRequest.builder()
                .bucket(bucketName)
                .versioningConfiguration(VersioningConfiguration.builder().status(status).build())
                .build();
        handleRequest(() -> client.putBucketVersioning(req));
        log.info("Set versioning status [{}] on bucket [{}]", status, bucketName);
    }

    private List<LifecycleRule> listLifecycleRuleModels(String bucketName) {
        GetBucketLifecycleConfigurationResponse resp = handleRequest(() ->
                client.getBucketLifecycleConfiguration(
                        GetBucketLifecycleConfigurationRequest.builder().bucket(bucketName).build()));
        if (resp == null) {
            return Collections.emptyList();
        }
        return resp.rules();
    }

    private List<CORSRule> listCorsRuleModels(String bucketName) {
        GetBucketCorsResponse resp = handleRequest(() ->
                client.getBucketCors(GetBucketCorsRequest.builder().bucket(bucketName).build()));
        if (resp == null) {
            return Collections.emptyList();
        }
        return resp.corsRules();
    }

    /**
     * 生命周期查询对外只暴露稳定字段。
     *
     * 当前示例页面主要面向“按前缀在 N 天后过期”这类规则，
     * 因此这里只保留前缀和过期信息，避免前端直接耦合 SDK 复杂结构。
     */
    private LifecycleRuleInfo toLifecycleRuleInfo(LifecycleRule rule) {
        LifecycleExpiration expiration = rule.expiration();
        return LifecycleRuleInfo.builder()
                .id(rule.id())
                .status(rule.statusAsString())
                .prefix(resolveLifecyclePrefix(rule))
                .expirationDays(expiration == null ? null : expiration.days())
                .expirationDate(expiration == null || expiration.date() == null ? null : Date.from(expiration.date()))
                .expiredObjectDeleteMarker(expiration == null ? null : expiration.expiredObjectDeleteMarker())
                .build();
    }

    private String resolveLifecyclePrefix(LifecycleRule rule) {
        if (rule.filter() != null && rule.filter().prefix() != null) {
            return rule.filter().prefix();
        }
        return rule.prefix();
    }

    private CorsRuleInfo toCorsRuleInfo(CORSRule rule) {
        return CorsRuleInfo.builder()
                .id(rule.id())
                .allowedOrigins(rule.hasAllowedOrigins() ? rule.allowedOrigins() : Collections.<String>emptyList())
                .allowedMethods(rule.hasAllowedMethods() ? rule.allowedMethods() : Collections.<String>emptyList())
                .allowedHeaders(rule.hasAllowedHeaders() ? rule.allowedHeaders() : Collections.<String>emptyList())
                .exposeHeaders(rule.hasExposeHeaders() ? rule.exposeHeaders() : Collections.<String>emptyList())
                .maxAgeSeconds(rule.maxAgeSeconds())
                .build();
    }
}
