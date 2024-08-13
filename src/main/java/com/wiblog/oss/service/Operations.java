package com.wiblog.oss.service;

import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;
import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.constant.ClientEnum;
import com.wiblog.oss.util.Util;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * @author panwm
 * @since 2023/8/22 0:26
 */
@Slf4j
public abstract class Operations {

    protected final OssProperties ossProperties;

    protected final S3AsyncClient client;

    protected final S3TransferManager transferManager;

    public Operations(OssProperties ossProperties, S3AsyncClient client, S3TransferManager transferManager) {
        this.ossProperties = ossProperties;
        this.client = client;
        this.transferManager = transferManager;
    }

    public <T> T handleRequest(Supplier<CompletableFuture<T>> requestSupplier) {
        try {
            // 执行并返回 CompletableFuture 的结果
            return requestSupplier.get().get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof NoSuchKeyException) {
                log.debug("OSS Error：文件不存在");
            } else if (e.getCause() instanceof S3Exception) {
                S3Exception s3Exception = (S3Exception) e.getCause();
                log.error("OSS Error: " + s3Exception.awsErrorDetails().errorMessage());
            } else {
                log.error(e.getMessage(), e);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("The operation was interrupted", e);
        }
        return null;
    }

    protected String formatPath(String path) {
        return Util.formatPath(path);
    }

    protected ObjectInfo buildObjectInfo(String key, Date lastModified, long size) {
        return ObjectInfo.builder()
                .uri(key)
                .name(Util.getFilename(key))
                .uploadTime(lastModified)
                .size(size)
                .ext(Util.getExtension(key))
                .build();
    }

    /**
     * 构造返回结构
     * @param key key
     * @param object object
     * @return ObjectResp
     */
    protected ObjectInfo buildObjectInfo(String key, HeadObjectResponse object) {
        return object == null ? null : ObjectInfo.builder()
                .uri(key)
                .url(getDomain() + key)
                .name(Util.getFilename(key))
                .uploadTime(Date.from(object.lastModified()))
                .size(object.contentLength())
                .ext(Util.getExtension(key))
                .build();
    }

    protected ObjectTreeNode buildTreeNode(S3Object object) {
        return object == null ? null :
                new ObjectTreeNode(Util.getFilename(object.key()), object.key(),
                        getDomain() + object.key(), Date.from(object.lastModified()), "file", object.size(), Util.getExtension(object.key()));
    }

    /**
     * 构造返回结构
     *
     * @param objectName objectName
     * @return ObjectResp
     */
    protected ObjectTreeNode buildTreeNode(String objectName) {
        if (objectName.endsWith("/")) {
            objectName = objectName.substring(0, objectName.length() - 1);
        }
        return new ObjectTreeNode(Util.getFilename(objectName), objectName, getDomain() + objectName, null, "folder", 0, null);
    }

    /**
     * 构造返回结构
     * @param objectName objectName
     * @param fileSize fileSize
     * @return ObjectInfo
     */
    protected ObjectInfo buildObjectInfo(String objectName, long fileSize) {
        if (objectName.endsWith("/")) {
            objectName = objectName.substring(0, objectName.length() - 1);
        }
        return ObjectInfo.builder()
                .uri(objectName)
                .url(getDomain() + objectName)
                .size(fileSize)
                .ext(Util.getExtension(objectName))
                .name(Util.getFilename(objectName))
                .uploadTime(new Date())
                .build();
    }

    protected String getDomain() {
        if (ClientEnum.OBS.getType().equals(ossProperties.getType())) {
            URL url;
            try {
                url = new URL(ossProperties.getEndpoint());
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
            return url.getProtocol() + "://" + ossProperties.getBucketName() + "." + url.getHost() + "/";
        } else {
            return ossProperties.getEndpoint() + "/" + ossProperties.getBucketName() + "/";
        }
    }
}
