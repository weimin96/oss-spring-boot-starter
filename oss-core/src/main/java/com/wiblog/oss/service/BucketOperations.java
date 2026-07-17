package com.wiblog.oss.service;

import com.wiblog.oss.bean.*;
import com.wiblog.oss.config.OssClientOptions;
import com.wiblog.oss.exception.OssException;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Bucket 级别操作。
 *
 * @author panwm
 */
@Slf4j
public class BucketOperations extends Operations implements OssBucketService {

    private static final String GROUP_ALL_USERS = "http://acs.amazonaws.com/groups/global/AllUsers";
    private static final String GROUP_AUTHENTICATED_USERS = "http://acs.amazonaws.com/groups/global/AuthenticatedUsers";

    /**
     * 创建 Bucket 管理操作门面。
     *
     * @param ossProperties   OSS 配置
     * @param client          S3 异步客户端
     * @param transferManager 传输管理器
     */
    public BucketOperations(OssClientOptions ossProperties, S3AsyncClient client,
                            S3TransferManager transferManager) {
        super(ossProperties, client, transferManager);
    }

    /**
     * 为默认 Bucket 启用版本控制。
     */
    @Override
    public void enableVersioning() {
        enableVersioning(ossProperties.getBucketName());
    }

    /**
     * 为指定 Bucket 启用版本控制。
     *
     * @param bucketName Bucket 名称
     */
    @Override
    public void enableVersioning(String bucketName) {
        setVersioningStatus(bucketName, BucketVersioningStatus.ENABLED);
    }

    /**
     * 挂起默认 Bucket 的版本控制。
     */
    @Override
    public void suspendVersioning() {
        suspendVersioning(ossProperties.getBucketName());
    }

    /**
     * 挂起指定 Bucket 的版本控制。
     *
     * @param bucketName Bucket 名称
     */
    @Override
    public void suspendVersioning(String bucketName) {
        setVersioningStatus(bucketName, BucketVersioningStatus.SUSPENDED);
    }

    /**
     * 查询默认 Bucket 的版本控制状态。
     *
     * @return 版本控制状态；未配置时返回 {@code null}
     */
    @Override
    public String getVersioningStatus() {
        return getVersioningStatus(ossProperties.getBucketName());
    }

    /**
     * 查询指定 Bucket 的版本控制状态。
     *
     * @param bucketName Bucket 名称
     * @return 版本控制状态；未配置时返回 {@code null}
     */
    @Override
    public String getVersioningStatus(String bucketName) {
        if (bucketName == null || bucketName.trim().isEmpty()) {
            throw new IllegalArgumentException("Bucket 名称不能为空");
        }
        try {
            GetBucketVersioningResponse response = executeRequestStrict(() ->
                    client.getBucketVersioning(GetBucketVersioningRequest.builder()
                            .bucket(bucketName)
                            .build()));
            return response == null || response.status() == null
                    ? null : response.status().toString();
        } catch (NoSuchBucketException exception) {
            throw OssException.bucketNotFound(bucketName);
        } catch (S3Exception exception) {
            String errorCode = extractS3ErrorCode(exception);
            if ("NoSuchBucket".equals(errorCode)) {
                throw OssException.bucketNotFound(bucketName);
            }
            if (exception.statusCode() == 403 || "AccessDenied".equals(errorCode)) {
                throw new OssException("BUCKET_VERSIONING_FORBIDDEN",
                        "没有查询 Bucket 版本控制状态的权限：" + bucketName, exception);
            }
            throw new OssException("BUCKET_VERSIONING_QUERY_FAILED",
                    "查询 Bucket 版本控制状态失败：" + bucketName, exception);
        } catch (OssException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OssException("BUCKET_VERSIONING_QUERY_FAILED",
                    "查询 Bucket 版本控制状态失败：" + bucketName, exception);
        }
    }

    /**
     * 查询指定 Bucket 的聚合详情。
     *
     * <p>详情接口会聚合创建时间、ACL、总对象数、总大小和标签，
     * 目的是给管理页和诊断场景提供一次性可展示的数据快照。</p>
     *
     * @param bucketName Bucket 名称
     * @return Bucket 详情
     */
    @Override
    public BucketDetailInfo getBucketDetail(String bucketName) {
        ensureBucketAccessible(bucketName);
        BucketStatistics statistics = collectBucketStatistics(bucketName);
        BucketAccessInfo accessInfo = queryBucketAccessInfo(bucketName);
        return BucketDetailInfo.builder()
                .name(bucketName)
                .creationDate(resolveBucketCreationDate(bucketName))
                .access(accessInfo.getAcl())
                .totalSize(statistics.getTotalSize())
                .totalObjectCount(statistics.getTotalObjectCount())
                .tags(getBucketTagsForDetail(bucketName))
                .build();
    }

    /**
     * 查询指定 Bucket 当前可识别的 ACL 信息。
     *
     * @param bucketName Bucket 名称
     * @return ACL 信息；底层不支持时会返回 `supported=false`
     */
    @Override
    public BucketAccessInfo getBucketAccess(String bucketName) {
        ensureBucketAccessible(bucketName);
        return queryBucketAccessInfo(bucketName);
    }

    /**
     * 设置指定 Bucket 的 ACL。
     *
     * <p>该方法只接受 S3 标准 canned ACL，
     * 这样可以在不同兼容实现之间保持明确且可预测的权限语义。</p>
     *
     * @param bucketName Bucket 名称
     * @param acl        S3 canned ACL，例如 `private`、`public-read`
     * @return 设置后的 ACL 信息
     */
    @Override
    public BucketAccessInfo setBucketAccess(String bucketName, String acl) {
        ensureBucketAccessible(bucketName);
        BucketCannedACL bucketCannedACL = parseBucketCannedAcl(acl);
        try {
            executeRequestStrict(() -> client.putBucketAcl(PutBucketAclRequest.builder()
                    .bucket(bucketName)
                    .acl(bucketCannedACL)
                    .build()));
            return buildSupportedBucketAccess(bucketName, bucketCannedACL.toString());
        } catch (S3Exception exception) {
            if (isBucketAclUnsupported(exception)) {
                throw new OssException("BUCKET_ACL_UNSUPPORTED",
                        "当前存储服务不支持 Bucket ACL 设置");
            }
            throw new OssException("BUCKET_ACL_UPDATE_FAILED",
                    "设置 Bucket ACL 失败");
        }
    }

    /**
     * 回滚通过“恢复一个历史版本或创建一个新的删除标记”来调整当前可见状态，
     * 而不是删除历史版本，这样可以保留完整审计轨迹。
     *
     * @param bucketName Bucket 名称
     * @param targetTime 目标时间点，必须是 ISO-8601 UTC 时间
     * @return 回滚结果统计
     */
    @Override
    public BucketRewindResult rewindBucket(String bucketName, String targetTime) {
        Instant targetInstant = parseTargetTime(targetTime);
        String versioningStatus = getVersioningStatus(bucketName);
        if (!BucketVersioningStatus.ENABLED.toString().equals(versioningStatus)) {
            throw new OssException("BUCKET_VERSIONING_REQUIRED",
                    "Bucket 必须处于 Enabled 状态才能执行按时间回滚：" + bucketName);
        }

        PutOperations copyOperations = new PutOperations(ossProperties, client, transferManager);
        Map<String, List<BucketHistoryEntry>> histories = collectBucketHistories(bucketName);
        long restoredObjectCount = 0L;
        long deletedObjectCount = 0L;
        long skippedObjectCount = 0L;

        for (Map.Entry<String, List<BucketHistoryEntry>> entry : histories.entrySet()) {
            BucketHistoryEntry currentState = findCurrentState(entry.getValue());
            BucketHistoryEntry targetState = findStateAtOrBefore(entry.getValue(), targetInstant);

            if (targetState == null || targetState.isDeleteMarker()) {
                if (currentState == null || currentState.isDeleteMarker()) {
                    skippedObjectCount++;
                    continue;
                }
                createDeleteMarker(bucketName, entry.getKey());
                deletedObjectCount++;
                continue;
            }

            if (currentState != null
                    && !currentState.isDeleteMarker()
                    && targetState.getVersionId().equals(currentState.getVersionId())) {
                skippedObjectCount++;
                continue;
            }

            restoreVersionAsLatest(copyOperations, bucketName, targetState);
            restoredObjectCount++;
        }

        return BucketRewindResult.builder()
                .bucketName(bucketName)
                .targetTime(targetInstant.toString())
                .scannedObjectCount(histories.size())
                .restoredObjectCount(restoredObjectCount)
                .deletedObjectCount(deletedObjectCount)
                .skippedObjectCount(skippedObjectCount)
                .build();
    }

    /**
     * 覆盖设置默认 Bucket 的生命周期规则。
     *
     * @param rules 生命周期规则集合
     */
    public void putLifecycleRules(List<LifecycleRule> rules) {
        putLifecycleRules(ossProperties.getBucketName(), rules);
    }

    /**
     * 覆盖设置指定 Bucket 的生命周期规则。
     *
     * @param bucketName Bucket 名称
     * @param rules      生命周期规则集合
     */
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

    /**
     * 查询默认 Bucket 的生命周期规则。
     *
     * @return 生命周期规则列表
     */
    @Override
    public List<LifecycleRuleInfo> getLifecycleRules() {
        return getLifecycleRules(ossProperties.getBucketName());
    }

    /**
     * 查询指定 Bucket 的生命周期规则。
     *
     * @param bucketName Bucket 名称
     * @return 生命周期规则列表
     */
    @Override
    public List<LifecycleRuleInfo> getLifecycleRules(String bucketName) {
        return listLifecycleRuleModels(bucketName).stream()
                .map(this::toLifecycleRuleInfo)
                .collect(Collectors.toList());
    }

    /**
     * 删除默认 Bucket 的全部生命周期规则。
     */
    @Override
    public void deleteLifecycleRules() {
        deleteLifecycleRules(ossProperties.getBucketName());
    }

    /**
     * 删除指定 Bucket 的全部生命周期规则。
     *
     * @param bucketName Bucket 名称
     */
    @Override
    public void deleteLifecycleRules(String bucketName) {
        requireSuccessfulRequest(() -> client.deleteBucketLifecycle(
                        DeleteBucketLifecycleRequest.builder().bucket(bucketName).build()),
                "BUCKET_LIFECYCLE_DELETE_FAILED",
                "删除 Bucket 生命周期规则失败：" + bucketName);
        log.info("Deleted lifecycle rules on bucket [{}]", bucketName);
    }

    /**
     * 为默认 Bucket 添加或替换一个过期删除规则。
     *
     * @param ruleId         规则 ID
     * @param prefix         规则作用前缀
     * @param expirationDays 过期天数
     */
    @Override
    public void addExpirationRule(String ruleId, String prefix, int expirationDays) {
        addExpirationRule(ossProperties.getBucketName(), ruleId, prefix, expirationDays);
    }

    /**
     * 为指定 Bucket 添加或替换一个过期删除规则。
     *
     * @param bucketName     Bucket 名称
     * @param ruleId         规则 ID
     * @param prefix         规则作用前缀
     * @param expirationDays 过期天数
     */
    @Override
    public void addExpirationRule(String bucketName, String ruleId, String prefix, int expirationDays) {
        LifecycleRule rule = LifecycleRule.builder()
                .id(ruleId)
                .status(ExpirationStatus.ENABLED)
                .filter(LifecycleRuleFilter.builder().prefix(prefix).build())
                .expiration(LifecycleExpiration.builder().days(expirationDays).build())
                .build();

        List<LifecycleRule> existing = new ArrayList<LifecycleRule>(listLifecycleRuleModels(bucketName));
        existing.removeIf(existingRule -> ruleId.equals(existingRule.id()));
        existing.add(rule);
        putLifecycleRules(bucketName, existing);
        log.info("Added expiration rule [{}] on bucket [{}]: {} days, prefix='{}'",
                ruleId, bucketName, expirationDays, prefix);
    }

    /**
     * 覆盖设置默认 Bucket 的 CORS 规则。
     *
     * @param corsRules CORS 规则集合
     */
    public void putCorsRules(List<CORSRule> corsRules) {
        putCorsRules(ossProperties.getBucketName(), corsRules);
    }

    /**
     * 覆盖设置指定 Bucket 的 CORS 规则。
     *
     * @param bucketName Bucket 名称
     * @param corsRules  CORS 规则集合
     */
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

    /**
     * 查询默认 Bucket 的 CORS 规则。
     *
     * @return CORS 规则列表
     */
    @Override
    public List<CorsRuleInfo> getCorsRules() {
        return getCorsRules(ossProperties.getBucketName());
    }

    /**
     * 查询指定 Bucket 的 CORS 规则。
     *
     * @param bucketName Bucket 名称
     * @return CORS 规则列表
     */
    @Override
    public List<CorsRuleInfo> getCorsRules(String bucketName) {
        return listCorsRuleModels(bucketName).stream()
                .map(this::toCorsRuleInfo)
                .collect(Collectors.toList());
    }

    /**
     * 为默认 Bucket 设置“允许所有来源”的 CORS 规则。
     */
    @Override
    public void allowAllOriginsCors() {
        allowAllOriginsCors(ossProperties.getBucketName());
    }

    /**
     * 为指定 Bucket 设置“允许所有来源”的 CORS 规则。
     *
     * @param bucketName Bucket 名称
     */
    @Override
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
     * 删除默认 Bucket 的 CORS 规则。
     */
    @Override
    public void deleteCorsRules() {
        deleteCorsRules(ossProperties.getBucketName());
    }

    /**
     * 删除指定 Bucket 的 CORS 规则。
     *
     * @param bucketName Bucket 名称
     */
    @Override
    public void deleteCorsRules(String bucketName) {
        requireSuccessfulRequest(() -> client.deleteBucketCors(
                        DeleteBucketCorsRequest.builder().bucket(bucketName).build()),
                "BUCKET_CORS_DELETE_FAILED",
                "删除 Bucket CORS 规则失败：" + bucketName);
    }

    /**
     * 查询默认 Bucket 的访问策略 JSON。
     *
     * @return 策略 JSON；未配置时返回 {@code null}
     */
    @Override
    public String getBucketPolicy() {
        return getBucketPolicy(ossProperties.getBucketName());
    }

    /**
     * 查询指定 Bucket 的访问策略 JSON。
     *
     * @param bucketName Bucket 名称
     * @return 策略 JSON；未配置时返回 {@code null}
     */
    @Override
    public String getBucketPolicy(String bucketName) {
        GetBucketPolicyResponse resp = handleRequest(() ->
                client.getBucketPolicy(GetBucketPolicyRequest.builder().bucket(bucketName).build()));
        return resp == null ? null : resp.policy();
    }

    /**
     * 为默认 Bucket 设置访问策略。
     *
     * @param policyJson 策略 JSON
     */
    @Override
    public void putBucketPolicy(String policyJson) {
        putBucketPolicy(ossProperties.getBucketName(), policyJson);
    }

    /**
     * 为指定 Bucket 设置访问策略。
     *
     * @param bucketName Bucket 名称
     * @param policyJson 策略 JSON
     */
    @Override
    public void putBucketPolicy(String bucketName, String policyJson) {
        requireSuccessfulRequest(() -> client.putBucketPolicy(
                        PutBucketPolicyRequest.builder().bucket(bucketName).policy(policyJson).build()),
                "BUCKET_POLICY_UPDATE_FAILED",
                "设置 Bucket 访问策略失败：" + bucketName);
        log.info("Updated policy on bucket [{}]", bucketName);
    }

    /**
     * 删除默认 Bucket 的访问策略。
     */
    @Override
    public void deleteBucketPolicy() {
        deleteBucketPolicy(ossProperties.getBucketName());
    }

    /**
     * 删除指定 Bucket 的访问策略。
     *
     * @param bucketName Bucket 名称
     */
    @Override
    public void deleteBucketPolicy(String bucketName) {
        requireSuccessfulRequest(() -> client.deleteBucketPolicy(
                        DeleteBucketPolicyRequest.builder().bucket(bucketName).build()),
                "BUCKET_POLICY_DELETE_FAILED",
                "删除 Bucket 访问策略失败：" + bucketName);
        log.info("Deleted policy on bucket [{}]", bucketName);
    }

    /**
     * 为默认 Bucket 开启公网访问屏蔽。
     */
    @Override
    public void blockAllPublicAccess() {
        blockAllPublicAccess(ossProperties.getBucketName());
    }

    /**
     * 为指定 Bucket 开启公网访问屏蔽。
     *
     * @param bucketName Bucket 名称
     */
    @Override
    public void blockAllPublicAccess(String bucketName) {
        PublicAccessBlockConfiguration config = PublicAccessBlockConfiguration.builder()
                .blockPublicAcls(true)
                .ignorePublicAcls(true)
                .blockPublicPolicy(true)
                .restrictPublicBuckets(true)
                .build();
        requireSuccessfulRequest(() -> client.putPublicAccessBlock(
                        PutPublicAccessBlockRequest.builder()
                                .bucket(bucketName)
                                .publicAccessBlockConfiguration(config)
                                .build()),
                "BUCKET_PUBLIC_ACCESS_BLOCK_FAILED",
                "开启 Bucket 公网访问屏蔽失败：" + bucketName);
        log.info("Blocked all public access on bucket [{}]", bucketName);
    }

    /**
     * 查询默认 Bucket 的公网访问屏蔽配置。
     *
     * @return 公网访问屏蔽配置；未配置时返回 {@code null}
     */
    public PublicAccessBlockConfiguration getPublicAccessBlock() {
        return getPublicAccessBlock(ossProperties.getBucketName());
    }

    /**
     * 查询指定 Bucket 的公网访问屏蔽配置。
     *
     * @param bucketName Bucket 名称
     * @return 公网访问屏蔽配置；未配置时返回 {@code null}
     */
    public PublicAccessBlockConfiguration getPublicAccessBlock(String bucketName) {
        GetPublicAccessBlockResponse resp = handleRequest(() ->
                client.getPublicAccessBlock(
                        GetPublicAccessBlockRequest.builder().bucket(bucketName).build()));
        return resp == null ? null : resp.publicAccessBlockConfiguration();
    }

    /**
     * 为默认 Bucket 启用服务端加密。
     */
    @Override
    public void enableServerSideEncryption() {
        enableServerSideEncryption(ossProperties.getBucketName());
    }

    /**
     * 为指定 Bucket 启用服务端加密。
     *
     * @param bucketName Bucket 名称
     */
    @Override
    public void enableServerSideEncryption(String bucketName) {
        ServerSideEncryptionRule rule = ServerSideEncryptionRule.builder()
                .applyServerSideEncryptionByDefault(
                        ServerSideEncryptionByDefault.builder()
                                .sseAlgorithm(ServerSideEncryption.AES256)
                                .build())
                .bucketKeyEnabled(true)
                .build();

        requireSuccessfulRequest(() -> client.putBucketEncryption(
                        PutBucketEncryptionRequest.builder()
                                .bucket(bucketName)
                                .serverSideEncryptionConfiguration(
                                        ServerSideEncryptionConfiguration.builder().rules(rule).build())
                                .build()),
                "BUCKET_ENCRYPTION_ENABLE_FAILED",
                "启用 Bucket 服务端加密失败：" + bucketName);
        log.info("Enabled SSE-S3 encryption on bucket [{}]", bucketName);
    }

    /**
     * 查询默认 Bucket 的服务端加密配置。
     *
     * @return 加密配置；未配置时返回 {@code null}
     */
    public ServerSideEncryptionConfiguration getEncryptionConfiguration() {
        return getEncryptionConfiguration(ossProperties.getBucketName());
    }

    /**
     * 查询指定 Bucket 的服务端加密配置。
     *
     * @param bucketName Bucket 名称
     * @return 加密配置；未配置时返回 {@code null}
     */
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
        requireSuccessfulRequest(() -> client.putBucketVersioning(req),
                "BUCKET_VERSIONING_UPDATE_FAILED",
                "设置 Bucket 版本控制失败：" + bucketName);
        log.info("Set versioning status [{}] on bucket [{}]", status, bucketName);
    }

    private void ensureBucketAccessible(String bucketName) {
        requireSuccessfulRequest(() -> client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build()),
                "BUCKET_NOT_FOUND",
                "未找到 Bucket：" + bucketName);
    }

    private Date resolveBucketCreationDate(String bucketName) {
        ListBucketsResponse response = requireSuccessfulRequest(client::listBuckets,
                "BUCKET_LIST_FAILED",
                "查询 Bucket 列表失败");
        for (Bucket bucket : response.buckets()) {
            if (bucketName.equals(bucket.name())) {
                return bucket.creationDate() == null ? null : Date.from(bucket.creationDate());
            }
        }
        return null;
    }

    private Map<String, String> getBucketTagsForDetail(String bucketName) {
        GetBucketTaggingResponse response = handleRequest(() ->
                client.getBucketTagging(GetBucketTaggingRequest.builder().bucket(bucketName).build()));
        if (response == null || !response.hasTagSet()) {
            return Collections.emptyMap();
        }
        return response.tagSet().stream().collect(Collectors.toMap(Tag::key, Tag::value));
    }

    private BucketStatistics collectBucketStatistics(String bucketName) {
        final long[] totalSize = new long[]{0L};
        final long[] totalObjectCount = new long[]{0L};
        client.listObjectsV2Paginator(builder -> builder.bucket(bucketName).maxKeys(1000))
                .subscribe(response -> response.contents().forEach(object -> {
                    totalSize[0] += object.size();
                    totalObjectCount[0]++;
                }))
                .join();
        return new BucketStatistics(totalSize[0], totalObjectCount[0]);
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
        LifecycleRuleFilter filter = rule.filter();
        if (filter != null && filter.prefix() != null) {
            return filter.prefix();
        }
        if (filter != null && filter.and() != null && filter.and().prefix() != null) {
            return filter.and().prefix();
        }
        return resolveLegacyLifecyclePrefix(rule);
    }

    private String resolveLegacyLifecyclePrefix(LifecycleRule rule) {
        // 旧版规则可能只有 Prefix 元素；这里通过字段级读取保留兼容性，同时避开已过时访问器。
        return rule.getValueForField("Prefix", String.class).orElse(null);
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

    private BucketAccessInfo queryBucketAccessInfo(String bucketName) {
        try {
            GetBucketAclResponse response = executeRequestStrict(
                    () -> client.getBucketAcl(GetBucketAclRequest.builder().bucket(bucketName).build()));
            return buildSupportedBucketAccess(bucketName, inferBucketAcl(response));
        } catch (S3Exception exception) {
            if (isBucketAclUnsupported(exception)) {
                return buildUnsupportedBucketAccess(bucketName, "当前存储服务不支持 Bucket ACL");
            }
            throw new OssException("BUCKET_ACL_QUERY_FAILED",
                    "获取 Bucket ACL 失败：" + bucketName, exception);
        }
    }

    private BucketAccessInfo buildSupportedBucketAccess(String bucketName, String acl) {
        return BucketAccessInfo.builder()
                .bucketName(bucketName)
                .acl(acl)
                .supported(true)
                .message("Bucket ACL 可用")
                .build();
    }

    private BucketAccessInfo buildUnsupportedBucketAccess(String bucketName, String message) {
        return BucketAccessInfo.builder()
                .bucketName(bucketName)
                .acl("unsupported")
                .supported(false)
                .message(message)
                .build();
    }

    private String inferBucketAcl(GetBucketAclResponse response) {
        Set<String> allUsersPermissions = new HashSet<>();
        Set<String> authenticatedUsersPermissions = new HashSet<>();
        Set<String> otherGroupUris = new HashSet<>();

        for (Grant grant : response.grants()) {
            if (grant.grantee() == null || grant.grantee().uri() == null || grant.permission() == null) {
                continue;
            }
            String uri = grant.grantee().uri();
            String permission = grant.permissionAsString();
            if (GROUP_ALL_USERS.equals(uri)) {
                allUsersPermissions.add(permission);
            } else if (GROUP_AUTHENTICATED_USERS.equals(uri)) {
                authenticatedUsersPermissions.add(permission);
            } else {
                otherGroupUris.add(uri);
            }
        }

        if (!otherGroupUris.isEmpty()) {
            return "custom";
        }
        if (allUsersPermissions.isEmpty() && authenticatedUsersPermissions.isEmpty()) {
            return BucketCannedACL.PRIVATE.toString();
        }
        if (allUsersPermissions.size() == 1
                && allUsersPermissions.contains(Permission.READ.toString())
                && authenticatedUsersPermissions.isEmpty()) {
            return BucketCannedACL.PUBLIC_READ.toString();
        }
        if (allUsersPermissions.size() == 2
                && allUsersPermissions.contains(Permission.READ.toString())
                && allUsersPermissions.contains(Permission.WRITE.toString())
                && authenticatedUsersPermissions.isEmpty()) {
            return BucketCannedACL.PUBLIC_READ_WRITE.toString();
        }
        if (authenticatedUsersPermissions.size() == 1
                && authenticatedUsersPermissions.contains(Permission.READ.toString())
                && allUsersPermissions.isEmpty()) {
            return BucketCannedACL.AUTHENTICATED_READ.toString();
        }
        return "custom";
    }

    private boolean isBucketAclUnsupported(S3Exception exception) {
        String errorCode = extractS3ErrorCode(exception);
        return "NotImplemented".equalsIgnoreCase(errorCode) || exception.statusCode() == 501;
    }

    private String extractS3ErrorCode(S3Exception exception) {
        return exception.awsErrorDetails() == null ? exception.getClass().getSimpleName()
                : exception.awsErrorDetails().errorCode();
    }

    private BucketCannedACL parseBucketCannedAcl(String acl) {
        if (acl == null || acl.trim().isEmpty()) {
            throw new OssException("BUCKET_ACL_INVALID", "Bucket ACL 不能为空");
        }
        BucketCannedACL parsedAcl = BucketCannedACL.fromValue(acl.trim());
        if (parsedAcl == null || BucketCannedACL.UNKNOWN_TO_SDK_VERSION.equals(parsedAcl)) {
            throw new OssException("BUCKET_ACL_INVALID", "不支持的 Bucket ACL：" + acl);
        }
        return parsedAcl;
    }

    private Instant parseTargetTime(String targetTime) {
        if (targetTime == null || targetTime.trim().isEmpty()) {
            throw new OssException("BUCKET_REWIND_TIME_INVALID", "回滚时间不能为空");
        }
        try {
            Instant parsedTargetTime = Instant.parse(targetTime.trim());
            if (parsedTargetTime.isAfter(Instant.now())) {
                throw new OssException("BUCKET_REWIND_TIME_INVALID", "回滚时间不能大于当前时间");
            }
            return parsedTargetTime;
        } catch (DateTimeParseException exception) {
            throw new OssException("BUCKET_REWIND_TIME_INVALID",
                    "回滚时间必须是 ISO-8601 UTC 时间，例如 1990-01-01T08:00:00Z", exception);
        }
    }

    private Map<String, List<BucketHistoryEntry>> collectBucketHistories(String bucketName) {
        Map<String, List<BucketHistoryEntry>> histories = new LinkedHashMap<String, List<BucketHistoryEntry>>();
        client.listObjectVersionsPaginator(ListObjectVersionsRequest.builder().bucket(bucketName).maxKeys(1000).build())
                .subscribe(response -> {
                    if (response.hasVersions()) {
                        for (ObjectVersion version : response.versions()) {
                            appendHistoryEntry(histories, BucketHistoryEntry.version(
                                    version.key(), version.versionId(), version.lastModified(),
                                    Boolean.TRUE.equals(version.isLatest())));
                        }
                    }
                    if (response.hasDeleteMarkers()) {
                        for (DeleteMarkerEntry deleteMarker : response.deleteMarkers()) {
                            appendHistoryEntry(histories, BucketHistoryEntry.deleteMarker(
                                    deleteMarker.key(), deleteMarker.versionId(), deleteMarker.lastModified(),
                                    Boolean.TRUE.equals(deleteMarker.isLatest())));
                        }
                    }
                }).join();

        for (List<BucketHistoryEntry> states : histories.values()) {
            states.sort(Comparator.comparing(BucketHistoryEntry::getLastModified).reversed());
        }
        return histories;
    }

    private void appendHistoryEntry(Map<String, List<BucketHistoryEntry>> histories, BucketHistoryEntry entry) {
        List<BucketHistoryEntry> entries = histories.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
        entries.add(entry);
    }

    private BucketHistoryEntry findCurrentState(List<BucketHistoryEntry> states) {
        for (BucketHistoryEntry state : states) {
            if (state.isLatest()) {
                return state;
            }
        }
        return states.isEmpty() ? null : states.get(0);
    }

    private BucketHistoryEntry findStateAtOrBefore(List<BucketHistoryEntry> states, Instant targetTime) {
        for (BucketHistoryEntry state : states) {
            if (!state.getLastModified().isAfter(targetTime)) {
                return state;
            }
        }
        return null;
    }

    private void createDeleteMarker(String bucketName, String key) {
        requireSuccessfulRequest(() -> client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build()),
                "BUCKET_REWIND_DELETE_FAILED",
                "回滚时删除当前对象失败：" + key);
    }

    private void restoreVersionAsLatest(PutOperations copyOperations, String bucketName,
                                        BucketHistoryEntry targetState) {
        try {
            copyOperations.copyObject(new CopyObjectCommand(
                    bucketName,
                    targetState.getKey(),
                    bucketName,
                    targetState.getKey(),
                    targetState.getVersionId()));
        } catch (OssException exception) {
            throw new OssException("BUCKET_REWIND_RESTORE_FAILED",
                    "回滚时恢复历史版本失败：" + targetState.getKey(), exception);
        }
    }

    private static final class BucketStatistics {
        private final long totalSize;
        private final long totalObjectCount;

        private BucketStatistics(long totalSize, long totalObjectCount) {
            this.totalSize = totalSize;
            this.totalObjectCount = totalObjectCount;
        }

        private long getTotalSize() {
            return totalSize;
        }

        private long getTotalObjectCount() {
            return totalObjectCount;
        }
    }

    private static final class BucketHistoryEntry {
        private final String key;
        private final String versionId;
        private final Instant lastModified;
        private final boolean deleteMarker;
        private final boolean latest;

        private BucketHistoryEntry(String key, String versionId, Instant lastModified,
                                   boolean deleteMarker, boolean latest) {
            this.key = key;
            this.versionId = versionId;
            this.lastModified = lastModified;
            this.deleteMarker = deleteMarker;
            this.latest = latest;
        }

        private static BucketHistoryEntry version(String key, String versionId, Instant lastModified, boolean latest) {
            return new BucketHistoryEntry(key, versionId, lastModified, false, latest);
        }

        private static BucketHistoryEntry deleteMarker(String key, String versionId, Instant lastModified, boolean latest) {
            return new BucketHistoryEntry(key, versionId, lastModified, true, latest);
        }

        private String getKey() {
            return key;
        }

        private String getVersionId() {
            return versionId;
        }

        private Instant getLastModified() {
            return lastModified;
        }

        private boolean isDeleteMarker() {
            return deleteMarker;
        }

        private boolean isLatest() {
            return latest;
        }
    }
}


