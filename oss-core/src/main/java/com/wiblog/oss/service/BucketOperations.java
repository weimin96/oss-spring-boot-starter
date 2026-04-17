package com.wiblog.oss.service;

import com.wiblog.oss.bean.BucketAccessInfo;
import com.wiblog.oss.bean.BucketDetailInfo;
import com.wiblog.oss.bean.BucketRewindResult;
import com.wiblog.oss.bean.CorsRuleInfo;
import com.wiblog.oss.bean.LifecycleRuleInfo;
import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.exception.OssException;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.BucketCannedACL;
import software.amazon.awssdk.services.s3.model.BucketLifecycleConfiguration;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.CORSConfiguration;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketLifecycleRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.DeleteMarkerEntry;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ExpirationStatus;
import software.amazon.awssdk.services.s3.model.GetBucketAclRequest;
import software.amazon.awssdk.services.s3.model.GetBucketAclResponse;
import software.amazon.awssdk.services.s3.model.GetBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.GetBucketCorsResponse;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionRequest;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionResponse;
import software.amazon.awssdk.services.s3.model.GetBucketLifecycleConfigurationRequest;
import software.amazon.awssdk.services.s3.model.GetBucketLifecycleConfigurationResponse;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyResponse;
import software.amazon.awssdk.services.s3.model.GetBucketTaggingRequest;
import software.amazon.awssdk.services.s3.model.GetBucketTaggingResponse;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningResponse;
import software.amazon.awssdk.services.s3.model.GetPublicAccessBlockRequest;
import software.amazon.awssdk.services.s3.model.GetPublicAccessBlockResponse;
import software.amazon.awssdk.services.s3.model.Grant;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.LifecycleExpiration;
import software.amazon.awssdk.services.s3.model.LifecycleRule;
import software.amazon.awssdk.services.s3.model.LifecycleRuleFilter;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ObjectVersion;
import software.amazon.awssdk.services.s3.model.Permission;
import software.amazon.awssdk.services.s3.model.PublicAccessBlockConfiguration;
import software.amazon.awssdk.services.s3.model.PutBucketAclRequest;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.PutBucketEncryptionRequest;
import software.amazon.awssdk.services.s3.model.PutBucketLifecycleConfigurationRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.PutPublicAccessBlockRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionByDefault;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionConfiguration;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionRule;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.VersioningConfiguration;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bucket 级别操作。
 *
 * @author panwm
 */
@Slf4j
public class BucketOperations extends Operations {

    private static final String GROUP_ALL_USERS = "http://acs.amazonaws.com/groups/global/AllUsers";
    private static final String GROUP_AUTHENTICATED_USERS = "http://acs.amazonaws.com/groups/global/AuthenticatedUsers";

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

    public BucketAccessInfo getBucketAccess(String bucketName) {
        ensureBucketAccessible(bucketName);
        return queryBucketAccessInfo(bucketName);
    }

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
     */
    public BucketRewindResult rewindBucket(String bucketName, String targetTime) {
        Instant targetInstant = parseTargetTime(targetTime);
        String versioningStatus = getVersioningStatus(bucketName);
        if (versioningStatus == null) {
            throw new OssException("BUCKET_VERSIONING_REQUIRED",
                    "Bucket 未开启版本控制，无法执行按时间回滚：" + bucketName);
        }

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

            restoreVersionAsLatest(bucketName, targetState);
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
        existing.removeIf(existingRule -> ruleId.equals(existingRule.id()));
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

    private void ensureBucketAccessible(String bucketName) {
        requireSuccessfulRequest(() -> client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build()),
                "BUCKET_NOT_FOUND",
                "未找到 Bucket：" + bucketName);
    }

    private Date resolveBucketCreationDate(String bucketName) {
        ListBucketsResponse response = requireSuccessfulRequest(() -> client.listBuckets(),
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
        Set<String> allUsersPermissions = new HashSet<String>();
        Set<String> authenticatedUsersPermissions = new HashSet<String>();
        Set<String> otherGroupUris = new HashSet<String>();

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
                    "回滚时间必须是 ISO-8601 UTC 时间，例如 2026-04-17T08:00:00Z", exception);
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
        List<BucketHistoryEntry> entries = histories.get(entry.getKey());
        if (entries == null) {
            entries = new ArrayList<BucketHistoryEntry>();
            histories.put(entry.getKey(), entries);
        }
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

    private void restoreVersionAsLatest(String bucketName, BucketHistoryEntry targetState) {
        requireSuccessfulRequest(() -> client.copyObject(CopyObjectRequest.builder()
                        .sourceBucket(bucketName)
                        .sourceKey(targetState.getKey())
                        .sourceVersionId(targetState.getVersionId())
                        .bucket(bucketName)
                        .key(targetState.getKey())
                        .build()),
                "BUCKET_REWIND_RESTORE_FAILED",
                "回滚时恢复历史版本失败：" + targetState.getKey());
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
