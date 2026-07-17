package com.wiblog.oss.service;

import com.wiblog.oss.config.OssClientOptions;
import com.wiblog.oss.exception.OssException;
import com.wiblog.oss.util.Util;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 删除操作。
 *
 * <p>目录删除默认优先走批量删除，以减少请求次数；当兼容实现不接受批量删除请求时，
 * 回退到逐对象删除，保证“目录中的对象最终被清理”这一业务语义稳定成立。</p>
 *
 * @author panwm
 */
@Slf4j
public class DeleteOperations extends Operations implements OssDeleteService {

    /**
     * S3 批量删除单次上限。
     */
    private static final int BATCH_DELETE_SIZE = 1000;

    /**
     * 创建删除操作门面。
     *
     * @param ossProperties   OSS 配置
     * @param client          S3 异步客户端
     * @param transferManager 传输管理器
     */
    public DeleteOperations(OssClientOptions ossProperties, S3AsyncClient client, S3TransferManager transferManager) {
        super(ossProperties, client, transferManager);
    }

    /**
     * 删除默认 Bucket 下的单个对象。
     *
     * @param objectName 对象 key
     */
    @Override
    public void removeObject(String objectName) {
        removeObject(ossProperties.getBucketName(), objectName);
    }

    /**
     * 删除指定 Bucket 下的单个对象。
     *
     * <p>单对象删除要求精确命中对象 key；如果对象不存在，方法会显式抛出领域异常，
     * 避免调用方误把”未删除任何内容”当成成功。</p>
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象 key
     */
    @Override
    public void removeObject(String bucketName, String objectName) {
        String normalizedKey = normalizeObjectKey(objectName);
        if (!deleteExactObjectIfExists(bucketName, normalizedKey)) {
            throw OssException.objectNotFound(normalizedKey);
        }
    }

    /**
     * 在默认 Bucket 下批量删除对象或目录。
     *
     * @param objectNames 对象 key 或目录前缀集合
     */
    @Override
    public void removeObjects(List<String> objectNames) {
        removeObjects(ossProperties.getBucketName(), objectNames);
    }

    /**
     * 批量删除支持”文件 key + 文件夹路径”混合输入。
     *
     * <p>这样设计的原因是前端批量操作通常来自多选结果，其中可能同时包含文件和目录。
     * 这里优先按精确对象命中，命不中且输入更像目录时，再按目录前缀展开为对象集合删除，
     * 从而避免把目录请求错误地当作单个占位对象删除。</p>
     */
    @Override
    public void removeObjects(String bucketName, List<String> objectNames) {
        LinkedHashSet<String> deleteKeys = resolveDeleteKeys(bucketName, objectNames);
        if (deleteKeys.isEmpty()) {
            throw new OssException("OBJECT_NOT_FOUND", "未命中任何可删除对象");
        }
        deleteByIdentifiers(bucketName, "批量删除", deleteKeys.stream()
                .map(this::toObjectIdentifier)
                .collect(Collectors.toList()));
    }

    /**
     * 删除默认 Bucket 下某个目录前缀对应的全部对象。
     *
     * @param path 目录前缀
     */
    @Override
    public void removeFolder(String path) {
        removeFolder(ossProperties.getBucketName(), path);
    }

    /**
     * 删除目录下的所有对象。
     *
     * <p>这里显式分页，是因为对象存储的列举结果可能超过单次上限；每一页删除完成后再继续拉取后续页，
     * 可以避免只删除第一页对象或陷入死循环。</p>
     */
    @Override
    public void removeFolder(String bucketName, String path) {
        String normalizedPath = Util.normalizeObjectPrefix(path);
        if (Util.isBlank(normalizedPath)) {
            throw new IllegalArgumentException("删除目录前缀不能为空");
        }
        List<ObjectIdentifier> objectIdentifiers = collectObjectIdentifiersByPrefix(bucketName, normalizedPath);
        if (objectIdentifiers.isEmpty()) {
            throw new OssException("OBJECT_NOT_FOUND", "未找到文件夹或文件夹下没有对象：" + normalizedPath);
        }
        deleteByIdentifiers(bucketName, normalizedPath, objectIdentifiers);
    }

    /**
     * 批量删除失败时回退到逐对象删除。
     *
     * <p>这样做的原因是部分兼容 S3 的实现会对批量删除额外要求 `Content-MD5` 等头部，
     * 但逐对象删除不受该限制。这里不吞掉失败，而是显式切换到兼容路径。</p>
     */
    private void deleteObjectsWithCompatibility(String bucketName, String path,
                                                List<ObjectIdentifier> objectIdentifiers) {
        DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                .bucket(bucketName)
                .delete(Delete.builder().objects(objectIdentifiers).build())
                .build();
        DeleteObjectsResponse deleteResponse = handleRequest(() -> client.deleteObjects(deleteRequest));
        if (deleteResponse == null) {
            log.warn("Batch delete failed under path [{}], fallback to single-object deletion", path);
            deleteObjectsOneByOne(bucketName, objectIdentifiers);
            return;
        }

        if (!deleteResponse.hasErrors()) {
            return;
        }

        Set<String> failedKeys = deleteResponse.errors().stream()
                .map(S3Error::key)
                .collect(Collectors.toCollection(HashSet::new));
        if (failedKeys.isEmpty()) {
            throw new OssException("OBJECT_BATCH_DELETE_FAILED",
                    "批量删除返回失败结果，但未提供失败对象 key：" + path);
        }

        log.warn("Batch delete had {} failed objects under path [{}], retrying individually",
                failedKeys.size(), path);
        deleteObjectsOneByOne(bucketName, objectIdentifiers.stream()
                .filter(objectIdentifier -> failedKeys.contains(objectIdentifier.key()))
                .collect(Collectors.toList()));
    }

    private void deleteObjectsOneByOne(String bucketName, List<ObjectIdentifier> objectIdentifiers) {
        for (ObjectIdentifier objectIdentifier : objectIdentifiers) {
            deleteExactObjectIfExists(bucketName, objectIdentifier.key());
        }
    }

    private LinkedHashSet<String> resolveDeleteKeys(String bucketName, List<String> objectNames) {
        LinkedHashSet<String> deleteKeys = new LinkedHashSet<>();
        if (objectNames == null || objectNames.isEmpty()) {
            return deleteKeys;
        }

        for (String rawTarget : objectNames) {
            String normalizedTarget = normalizeObjectKey(rawTarget);
            if (Util.isBlank(normalizedTarget)) {
                continue;
            }

            if (deleteKeys.contains(normalizedTarget)) {
                continue;
            }

            if (objectExists(bucketName, normalizedTarget)) {
                deleteKeys.add(normalizedTarget);
                continue;
            }

            collectObjectIdentifiersByPrefix(bucketName, Util.normalizeObjectPrefix(normalizedTarget)).stream()
                    .map(ObjectIdentifier::key)
                    .forEach(deleteKeys::add);
        }

        return deleteKeys;
    }

    private void deleteByIdentifiers(String bucketName, String operationName,
                                     List<ObjectIdentifier> objectIdentifiers) {
        if (shouldUseSingleDeleteForCompatibility()) {
            // AWS SDK 2.30+ 在 deleteObjects 上对 MinIO 等兼容实现存在已知的 Content-MD5 兼容性问题，
            // 这里直接走逐对象删除，避免先触发告警再回退，保证 CI 日志和运行行为稳定。
            deleteObjectsOneByOne(bucketName, objectIdentifiers);
            log.debug("{} completed with single-object delete fallback, deleted {} objects",
                    operationName, objectIdentifiers.size());
            return;
        }
        for (int start = 0; start < objectIdentifiers.size(); start += BATCH_DELETE_SIZE) {
            int end = Math.min(start + BATCH_DELETE_SIZE, objectIdentifiers.size());
            List<ObjectIdentifier> batch = new ArrayList<>(objectIdentifiers.subList(start, end));
            deleteObjectsWithCompatibility(bucketName, operationName, batch);
        }
        log.debug("{} completed, deleted {} objects", operationName, objectIdentifiers.size());
    }

    private boolean shouldUseSingleDeleteForCompatibility() {
        String storageType = ossProperties.getType();
        return storageType != null && !"s3".equalsIgnoreCase(storageType);
    }

    private List<ObjectIdentifier> collectObjectIdentifiersByPrefix(String bucketName, String prefix) {
        List<ObjectIdentifier> objectIdentifiers = new ArrayList<>();
        client.listObjectsV2Paginator(ListObjectsV2Request.builder()
                        .bucket(bucketName)
                        .prefix(prefix)
                        .maxKeys(BATCH_DELETE_SIZE)
                        .build())
                .subscribe(response -> response.contents().stream()
                        .map(S3Object::key)
                        .map(this::normalizeObjectKey)
                        .map(this::toObjectIdentifier)
                        .forEach(objectIdentifiers::add))
                .join();
        return objectIdentifiers;
    }

    private boolean deleteExactObjectIfExists(String bucketName, String objectKey) {
        if (!objectExists(bucketName, objectKey)) {
            return false;
        }
        requireSuccessfulRequest(() -> client.deleteObject(x -> x.bucket(bucketName).key(objectKey).build()),
                "OBJECT_DELETE_FAILED",
                "删除对象失败：" + objectKey);
        return true;
    }

    private boolean objectExists(String bucketName, String objectKey) {
        try {
            executeRequestStrict(() -> client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build()));
            return true;
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (NoSuchBucketException exception) {
            throw OssException.bucketNotFound(bucketName);
        } catch (S3Exception exception) {
            String errorCode = exception.awsErrorDetails() == null
                    ? null : exception.awsErrorDetails().errorCode();
            if ("NoSuchBucket".equals(errorCode)) {
                throw OssException.bucketNotFound(bucketName);
            }
            if (exception.statusCode() == 404 || "NoSuchKey".equals(errorCode)) {
                return false;
            }
            if (exception.statusCode() == 403 || "AccessDenied".equals(errorCode)) {
                throw new OssException("OBJECT_DELETE_FORBIDDEN",
                        "没有对象删除权限：" + objectKey, exception);
            }
            throw new OssException("OBJECT_DELETE_CHECK_FAILED",
                    "删除前检查对象失败：" + objectKey, exception);
        } catch (OssException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OssException("OBJECT_DELETE_CHECK_FAILED",
                    "删除前检查对象失败：" + objectKey, exception);
        }
    }

    private ObjectIdentifier toObjectIdentifier(String key) {
        return ObjectIdentifier.builder().key(normalizeObjectKey(key)).build();
    }

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


