package com.wiblog.oss.service;

import com.wiblog.oss.bean.OssProperties;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
public class DeleteOperations extends Operations {

    /**
     * S3 批量删除单次上限。
     */
    private static final int BATCH_DELETE_SIZE = 1000;

    public DeleteOperations(OssProperties ossProperties, S3AsyncClient client, S3TransferManager transferManager) {
        super(ossProperties, client, transferManager);
    }

    public void removeObject(String objectName) {
        removeObject(ossProperties.getBucketName(), objectName);
    }

    public void removeObject(String bucketName, String objectName) {
        DeleteObjectResponse ignored = handleRequest(() -> client.deleteObject(x -> x.bucket(bucketName)
                .key(formatPath(objectName)).build()));
    }

    public void removeFolder(String path) {
        removeFolder(ossProperties.getBucketName(), path);
    }

    /**
     * 删除目录下的所有对象。
     *
     * <p>这里显式分页，是因为对象存储的列举结果可能超过单次上限；每一页删除完成后再继续拉取后续页，
     * 可以避免只删除第一页对象或陷入死循环。</p>
     */
    public void removeFolder(String bucketName, String path) {
        String continuationToken = null;
        do {
            ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(path)
                    .maxKeys(BATCH_DELETE_SIZE);
            if (continuationToken != null) {
                requestBuilder.continuationToken(continuationToken);
            }

            ListObjectsV2Response response = handleRequest(() -> client.listObjectsV2(requestBuilder.build()));
            if (response == null || response.contents().isEmpty()) {
                break;
            }

            List<ObjectIdentifier> objectIdentifiers = response.contents().stream()
                    .map(s3Object -> ObjectIdentifier.builder().key(formatPath(s3Object.key())).build())
                    .collect(Collectors.toList());
            deleteObjectsWithCompatibility(bucketName, path, objectIdentifiers);
            log.debug("Deleted {} objects under path [{}]", objectIdentifiers.size(), path);

            continuationToken = response.nextContinuationToken();
        } while (continuationToken != null);
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
            return;
        }

        log.warn("Batch delete had {} failed objects under path [{}], retrying individually",
                failedKeys.size(), path);
        deleteObjectsOneByOne(bucketName, objectIdentifiers.stream()
                .filter(objectIdentifier -> failedKeys.contains(objectIdentifier.key()))
                .toList());
    }

    private void deleteObjectsOneByOne(String bucketName, List<ObjectIdentifier> objectIdentifiers) {
        for (ObjectIdentifier objectIdentifier : objectIdentifiers) {
            removeObject(bucketName, objectIdentifier.key());
        }
    }
}
