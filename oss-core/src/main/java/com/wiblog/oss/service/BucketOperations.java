package com.wiblog.oss.service;

import com.wiblog.oss.bean.OssProperties;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.util.Collections;
import java.util.List;

/**
 * Bucket 级别操作。
 *
 * <p>覆盖 AWS S3 SDK v2 中常用的 Bucket 管理能力：</p>
 * <ul>
 *   <li>版本控制（Versioning）</li>
 *   <li>生命周期规则（Lifecycle）</li>
 *   <li>CORS 配置</li>
 *   <li>存储加密配置（SSE）</li>
 *   <li>访问控制策略（Bucket Policy）</li>
 *   <li>Public Access Block（阻止公共访问）</li>
 * </ul>
 *
 * @author panwm
 */
@Slf4j
public class BucketOperations extends Operations {

    public BucketOperations(OssProperties ossProperties, S3AsyncClient client,
                            S3TransferManager transferManager) {
        super(ossProperties, client, transferManager);
    }

    // ----------------------------------------------------------------
    // 版本控制
    // ----------------------------------------------------------------

    /**
     * 启用当前 Bucket 的版本控制。
     */
    public void enableVersioning() {
        enableVersioning(ossProperties.getBucketName());
    }

    public void enableVersioning(String bucketName) {
        setVersioningStatus(bucketName, BucketVersioningStatus.ENABLED);
    }

    /**
     * 挂起（暂停）版本控制（已有版本保留，新上传不产生版本）。
     */
    public void suspendVersioning() {
        suspendVersioning(ossProperties.getBucketName());
    }

    public void suspendVersioning(String bucketName) {
        setVersioningStatus(bucketName, BucketVersioningStatus.SUSPENDED);
    }

    /**
     * 获取 Bucket 版本控制状态。
     *
     * @return "Enabled" / "Suspended" / null（未开启）
     */
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

    // ----------------------------------------------------------------
    // 生命周期规则
    // ----------------------------------------------------------------

    /**
     * 设置 Bucket 生命周期规则（覆盖所有已有规则）。
     *
     * @param rules 生命周期规则列表
     */
    public void putLifecycleRules(List<LifecycleRule> rules) {
        putLifecycleRules(ossProperties.getBucketName(), rules);
    }

    public void putLifecycleRules(String bucketName, List<LifecycleRule> rules) {
        PutBucketLifecycleConfigurationRequest req = PutBucketLifecycleConfigurationRequest.builder()
                .bucket(bucketName)
                .lifecycleConfiguration(BucketLifecycleConfiguration.builder().rules(rules).build())
                .build();
        handleRequest(() -> client.putBucketLifecycleConfiguration(req));
        log.info("Set {} lifecycle rules on bucket [{}]", rules.size(), bucketName);
    }

    /**
     * 获取 Bucket 生命周期规则。
     *
     * @return 规则列表，若未配置则返回空列表
     */
    public List<LifecycleRule> getLifecycleRules() {
        return getLifecycleRules(ossProperties.getBucketName());
    }

    public List<LifecycleRule> getLifecycleRules(String bucketName) {
        GetBucketLifecycleConfigurationResponse resp = handleRequest(() ->
                client.getBucketLifecycleConfiguration(
                        GetBucketLifecycleConfigurationRequest.builder().bucket(bucketName).build()));
        if (resp == null) {
            return Collections.emptyList();
        }
        return resp.rules();
    }

    /**
     * 删除 Bucket 所有生命周期规则。
     */
    public void deleteLifecycleRules() {
        deleteLifecycleRules(ossProperties.getBucketName());
    }

    public void deleteLifecycleRules(String bucketName) {
        handleRequest(() -> client.deleteBucketLifecycle(
                DeleteBucketLifecycleRequest.builder().bucket(bucketName).build()));
        log.info("Deleted lifecycle rules on bucket [{}]", bucketName);
    }

    /**
     * 便捷方法：添加一条"N天后删除过期文件"的生命周期规则。
     *
     * @param ruleId         规则 ID（全局唯一）
     * @param prefix         作用路径前缀，传 "" 表示全 Bucket
     * @param expirationDays 过期天数
     */
    public void addExpirationRule(String ruleId, String prefix, int expirationDays) {
        addExpirationRule(ossProperties.getBucketName(), ruleId, prefix, expirationDays);
    }

    public void addExpirationRule(String bucketName, String ruleId, String prefix, int expirationDays) {
        LifecycleRule rule = LifecycleRule.builder()
                .id(ruleId)
                .status(ExpirationStatus.ENABLED)
                .filter(LifecycleRuleFilter.builder()
                        .prefix(prefix)
                        .build())
                .expiration(LifecycleExpiration.builder()
                        .days(expirationDays)
                        .build())
                .build();

        // 合并已有规则
        List<LifecycleRule> existing = new java.util.ArrayList<>(getLifecycleRules(bucketName));
        existing.removeIf(r -> ruleId.equals(r.id())); // 替换同 ID 的规则
        existing.add(rule);
        putLifecycleRules(bucketName, existing);
        log.info("Added expiration rule [{}] on bucket [{}]: {} days, prefix='{}'",
                ruleId, bucketName, expirationDays, prefix);
    }

    // ----------------------------------------------------------------
    // CORS 配置
    // ----------------------------------------------------------------

    /**
     * 设置 Bucket CORS 规则。
     *
     * @param corsRules CORS 规则列表
     */
    public void putCorsRules(List<CORSRule> corsRules) {
        putCorsRules(ossProperties.getBucketName(), corsRules);
    }

    public void putCorsRules(String bucketName, List<CORSRule> corsRules) {
        PutBucketCorsRequest req = PutBucketCorsRequest.builder()
                .bucket(bucketName)
                .corsConfiguration(CORSConfiguration.builder().corsRules(corsRules).build())
                .build();
        handleRequest(() -> client.putBucketCors(req));
        log.info("Set CORS rules on bucket [{}]", bucketName);
    }

    /**
     * 获取 Bucket CORS 配置。
     */
    public List<CORSRule> getCorsRules() {
        return getCorsRules(ossProperties.getBucketName());
    }

    public List<CORSRule> getCorsRules(String bucketName) {
        GetBucketCorsResponse resp = handleRequest(() ->
                client.getBucketCors(GetBucketCorsRequest.builder().bucket(bucketName).build()));
        if (resp == null) {
            return Collections.emptyList();
        }
        return resp.corsRules();
    }

    /**
     * 便捷方法：为前端直传场景配置宽松 CORS（允许所有来源）。
     */
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

    /**
     * 删除 Bucket CORS 配置。
     */
    public void deleteCorsRules() {
        deleteCorsRules(ossProperties.getBucketName());
    }

    public void deleteCorsRules(String bucketName) {
        handleRequest(() -> client.deleteBucketCors(
                DeleteBucketCorsRequest.builder().bucket(bucketName).build()));
    }

    // ----------------------------------------------------------------
    // Bucket 策略（Policy）
    // ----------------------------------------------------------------

    /**
     * 获取 Bucket 访问策略 JSON 字符串。
     *
     * @return JSON 策略字符串，若未配置返回 null
     */
    public String getBucketPolicy() {
        return getBucketPolicy(ossProperties.getBucketName());
    }

    public String getBucketPolicy(String bucketName) {
        GetBucketPolicyResponse resp = handleRequest(() ->
                client.getBucketPolicy(GetBucketPolicyRequest.builder().bucket(bucketName).build()));
        return resp == null ? null : resp.policy();
    }

    /**
     * 设置 Bucket 访问策略。
     *
     * @param policyJson JSON 格式的 IAM 策略
     */
    public void putBucketPolicy(String policyJson) {
        putBucketPolicy(ossProperties.getBucketName(), policyJson);
    }

    public void putBucketPolicy(String bucketName, String policyJson) {
        handleRequest(() -> client.putBucketPolicy(
                PutBucketPolicyRequest.builder().bucket(bucketName).policy(policyJson).build()));
        log.info("Updated policy on bucket [{}]", bucketName);
    }

    /**
     * 删除 Bucket 访问策略。
     */
    public void deleteBucketPolicy() {
        deleteBucketPolicy(ossProperties.getBucketName());
    }

    public void deleteBucketPolicy(String bucketName) {
        handleRequest(() -> client.deleteBucketPolicy(
                DeleteBucketPolicyRequest.builder().bucket(bucketName).build()));
        log.info("Deleted policy on bucket [{}]", bucketName);
    }

    // ----------------------------------------------------------------
    // Public Access Block（公共访问屏蔽）
    // ----------------------------------------------------------------

    /**
     * 启用所有公共访问屏蔽（最高安全级别）。
     */
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

    /**
     * 获取 Bucket 公共访问屏蔽配置。
     */
    public PublicAccessBlockConfiguration getPublicAccessBlock() {
        return getPublicAccessBlock(ossProperties.getBucketName());
    }

    public PublicAccessBlockConfiguration getPublicAccessBlock(String bucketName) {
        GetPublicAccessBlockResponse resp = handleRequest(() ->
                client.getPublicAccessBlock(
                        GetPublicAccessBlockRequest.builder().bucket(bucketName).build()));
        return resp == null ? null : resp.publicAccessBlockConfiguration();
    }

    // ----------------------------------------------------------------
    // 服务端加密（SSE）
    // ----------------------------------------------------------------

    /**
     * 为 Bucket 启用 AES-256 服务端加密（SSE-S3）。
     */
    public void enableServerSideEncryption() {
        enableServerSideEncryption(ossProperties.getBucketName());
    }

    public void enableServerSideEncryption(String bucketName) {
        ServerSideEncryptionRule rule = ServerSideEncryptionRule.builder()
                .applyServerSideEncryptionByDefault(
                        ServerSideEncryptionByDefault.builder()
                                .sseAlgorithm(ServerSideEncryption.AES256)
                                .build())
                .bucketKeyEnabled(true)  // 减少 KMS 调用成本
                .build();

        handleRequest(() -> client.putBucketEncryption(
                PutBucketEncryptionRequest.builder()
                        .bucket(bucketName)
                        .serverSideEncryptionConfiguration(
                                ServerSideEncryptionConfiguration.builder().rules(rule).build())
                        .build()));
        log.info("Enabled SSE-S3 encryption on bucket [{}]", bucketName);
    }

    /**
     * 获取 Bucket 加密配置。
     */
    public ServerSideEncryptionConfiguration getEncryptionConfiguration() {
        return getEncryptionConfiguration(ossProperties.getBucketName());
    }

    public ServerSideEncryptionConfiguration getEncryptionConfiguration(String bucketName) {
        GetBucketEncryptionResponse resp = handleRequest(() ->
                client.getBucketEncryption(
                        GetBucketEncryptionRequest.builder().bucket(bucketName).build()));
        return resp == null ? null : resp.serverSideEncryptionConfiguration();
    }

    // ----------------------------------------------------------------
    // 私有工具
    // ----------------------------------------------------------------

    private void setVersioningStatus(String bucketName, BucketVersioningStatus status) {
        PutBucketVersioningRequest req = PutBucketVersioningRequest.builder()
                .bucket(bucketName)
                .versioningConfiguration(VersioningConfiguration.builder().status(status).build())
                .build();
        handleRequest(() -> client.putBucketVersioning(req));
        log.info("Set versioning status [{}] on bucket [{}]", status, bucketName);
    }
}