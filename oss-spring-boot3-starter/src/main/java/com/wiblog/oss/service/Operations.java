package com.wiblog.oss.service;

import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;
import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.exception.OssException;
import com.wiblog.oss.service.strategy.DomainStrategy;
import com.wiblog.oss.service.strategy.DomainStrategyFactory;
import com.wiblog.oss.util.Util;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Operations 基类（模板方法 + 策略模式）
 *
 * <b>改进点：</b>
 * 1. 引入策略模式解耦域名构建逻辑，移除 if-else 类型判断，符合开闭原则。
 * 2. domainPrefix 在构造时缓存，避免每次 getDomain() 重新解析 URL（原代码问题）。
 * 3. handleRequest 区分业务异常/协议异常/系统异常，分级处理。
 * 4. 统一使用 OssException 代替裸 RuntimeException，增强可维护性。
 * 5. 工厂方法集中在基类，消除子类中的重复 URL 拼接代码。
 *
 * @author panwm
 */
@Slf4j
public abstract class Operations {

    protected final OssProperties ossProperties;
    protected final S3AsyncClient client;
    protected final S3TransferManager transferManager;

    /**
     * 构造时由策略计算并缓存，避免重复解析
     */
    private final String domainPrefix;

    protected Operations(OssProperties ossProperties,
                         S3AsyncClient client,
                         S3TransferManager transferManager) {
        this.ossProperties = ossProperties;
        this.client = client;
        this.transferManager = transferManager;
        DomainStrategy strategy = DomainStrategyFactory.getStrategy(ossProperties.getType());
        this.domainPrefix = strategy.buildDomain(ossProperties.getEndpoint(), ossProperties.getBucketName());
    }

    /**
     * 统一异步请求处理（模板方法）
     */
    public <T> T handleRequest(Supplier<CompletableFuture<T>> requestSupplier) {
        try {
            return requestSupplier.get().get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchKeyException) {
                log.debug("OSS: object does not exist");
                return null;
            } else if (cause instanceof S3Exception s3Ex) {
                String errorCode = s3Ex.awsErrorDetails() == null ? "UNKNOWN" : s3Ex.awsErrorDetails().errorCode();
                String errorMessage = s3Ex.awsErrorDetails() == null
                        ? s3Ex.getMessage()
                        : s3Ex.awsErrorDetails().errorMessage();
                log.warn("OSS S3 error [{}]: {}", errorCode, errorMessage);
                return null;
            } else {
                log.error("OSS unexpected error: {}", e.getMessage(), e);
                throw new OssException("OSS_ERROR", "Unexpected OSS error: " + e.getMessage(), cause);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OssException("OSS_INTERRUPTED", "OSS operation was interrupted", e);
        }
    }

    protected String formatPath(String path) {
        return Util.formatPath(path);
    }

    /**
     * 获取已缓存的域名前缀（以"/"结尾）
     *
     * @return domain
     */
    protected String getDomain() {
        return domainPrefix;
    }

    /**
     * ObjectInfo 工厂方法
     */
    protected ObjectInfo buildObjectInfo(String key, Date lastModified, long size) {
        return ObjectInfo.builder()
                .uri(key).url(domainPrefix + key)
                .name(Util.getFilename(key)).uploadTime(lastModified)
                .size(size).ext(Util.getExtension(key)).build();
    }

    protected ObjectInfo buildObjectInfo(String key, HeadObjectResponse object) {
        if (object == null) {
            return null;
        }
        return ObjectInfo.builder()
                .uri(key).url(domainPrefix + key)
                .name(Util.getFilename(key))
                .uploadTime(Date.from(object.lastModified()))
                .size(object.contentLength())
                .ext(Util.getExtension(key)).build();
    }

    protected ObjectInfo buildObjectInfo(String objectName, long fileSize) {
        String n = stripTrailingSlash(objectName);
        return ObjectInfo.builder()
                .uri(n).url(domainPrefix + n).size(fileSize)
                .ext(Util.getExtension(n)).name(Util.getFilename(n))
                .uploadTime(new Date()).build();
    }

    /**
     * ObjectTreeNode 工厂方法
     *
     * @param object s3对象
     * @return 树节点
     */
    protected ObjectTreeNode buildTreeNode(S3Object object) {
        if (object == null) {
            return null;
        }
        return new ObjectTreeNode(Util.getFilename(object.key()), object.key(),
                domainPrefix + object.key(), Date.from(object.lastModified()),
                "file", object.size(), Util.getExtension(object.key()));
    }

    protected ObjectTreeNode buildTreeNode(String objectName) {
        String n = stripTrailingSlash(objectName);
        return new ObjectTreeNode(Util.getFilename(n), n, domainPrefix + n,
                null, "folder", 0, null);
    }

    private static String stripTrailingSlash(String path) {
        return (path != null && path.endsWith("/")) ? path.substring(0, path.length() - 1) : path;
    }
}
