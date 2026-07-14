package com.wiblog.oss.service;

import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;
import com.wiblog.oss.bean.StoredObject;
import com.wiblog.oss.config.OssClientOptions;
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
 * <p>
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

    protected final OssClientOptions ossProperties;
    protected final S3AsyncClient client;
    protected final S3TransferManager transferManager;

    /**
     * 构造时由策略计算并缓存，避免重复解析
     */
    private final String domainPrefix;

    protected Operations(OssClientOptions ossProperties,
                         S3AsyncClient client,
                         S3TransferManager transferManager) {
        this.ossProperties = ossProperties;
        this.client = client;
        this.transferManager = transferManager;
        DomainStrategy strategy = DomainStrategyFactory.getStrategy(ossProperties.getType());
        this.domainPrefix = strategy.buildDomain(ossProperties.getEndpoint(), ossProperties.getBucketName());
    }

    /**
     * 统一处理“可接受空值折叠”的异步请求。
     *
     * <p>这个模板方法适用于读操作和存在性判断：当底层返回对象不存在、协议异常或某些兼容错误时，
     * 允许上层通过 `null` 或 `false` 再次收敛业务语义，而不是立刻向外抛异常。</p>
     *
     * @param requestSupplier 异步请求供应器
     * @param <T>             返回值类型
     * @return 成功时返回实际结果；部分可容忍失败场景返回 {@code null}
     */
    public <T> T handleRequest(Supplier<CompletableFuture<T>> requestSupplier) {
        try {
            return requestSupplier.get().get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchKeyException) {
                log.debug("OSS: object does not exist");
                return null;
            } else if (cause instanceof S3Exception) {
                S3Exception s3Ex = (S3Exception) cause;
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

    /**
     * 对需要保留底层 S3 错误语义的请求进行严格执行。
     * <p>
     * 某些能力需要根据 S3 返回的错误码区分“对象不存在”“能力不支持”“权限不足”等分支，
     * 如果继续走 handleRequest 的空值折叠逻辑，就无法给调用方提供准确的领域反馈。
     */
    protected <T> T executeRequestStrict(Supplier<CompletableFuture<T>> requestSupplier) {
        try {
            return requestSupplier.get().get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new OssException("OSS_ERROR", "Unexpected OSS error: " + e.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OssException("OSS_INTERRUPTED", "OSS operation was interrupted", e);
        }
    }

    /**
     * 对必须显式成功的请求进行约束。
     * <p>
     * 某些 S3 兼容实现会把权限、能力不支持等问题折叠成异常，
     * 而 handleRequest 会把这类异常转换成 null。
     * 对写操作和必须命中的查询，如果继续把 null 当成功处理，
     * 就会出现“接口返回成功，但实际没有生效”的假象。
     */
    protected <T> T requireSuccessfulRequest(Supplier<CompletableFuture<T>> requestSupplier,
                                             String errorCode, String errorMessage) {
        T response = handleRequest(requestSupplier);
        if (response == null) {
            throw new OssException(errorCode, errorMessage);
        }
        return response;
    }

    protected String formatPath(String path) {
        return Util.formatPath(path);
    }

    /**
     * 获取已缓存的域名前缀（以"/"结尾）。
     *
     * <p>域名前缀在构造阶段就已根据策略计算完成，
     * 这里直接返回缓存值，避免每次构建对象信息都重复解析 endpoint。</p>
     *
     * @return 域名前缀
     */
    protected String getDomain() {
        return domainPrefix;
    }

    /**
     * 根据基础元数据构建 {@link ObjectInfo}。
     *
     * @param key          对象 key
     * @param lastModified 最后修改时间
     * @param size         对象大小
     * @return 标准化后的对象信息
     */
    protected ObjectInfo buildObjectInfo(String key, Date lastModified, long size) {
        return ObjectInfo.builder()
                .uri(key).url(domainPrefix + key)
                .name(Util.getFilename(key)).uploadTime(lastModified)
                .size(size).ext(Util.getExtension(key)).build();
    }

    /**
     * 根据 {@link HeadObjectResponse} 构建 {@link ObjectInfo}。
     *
     * @param key    对象 key
     * @param object 头信息响应
     * @return 对象信息；响应为空时返回 {@code null}
     */
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

    protected StoredObject buildStoredObject(String bucket, String key, HeadObjectResponse object) {
        if (object == null || object.contentLength() == null) {
            throw new OssException("INVALID_HEAD_RESPONSE", "对象元数据缺少内容长度：" + key);
        }
        return new StoredObject(bucket, key, object.contentLength(), object.eTag(),
                object.versionId(), object.checksumSHA256());
    }

    /**
     * 根据对象名和文件大小构建一个即时对象信息。
     *
     * <p>该重载主要用于上传完成但不需要再次发起 HEAD 查询的场景，
     * 例如本地文件上传时，调用方已经掌握最终大小。</p>
     *
     * @param objectName 对象 key
     * @param fileSize   文件大小
     * @return 对象信息
     */
    protected ObjectInfo buildObjectInfo(String objectName, long fileSize) {
        String n = stripTrailingSlash(objectName);
        return ObjectInfo.builder()
                .uri(n).url(domainPrefix + n).size(fileSize)
                .ext(Util.getExtension(n)).name(Util.getFilename(n))
                .uploadTime(new Date()).build();
    }

    /**
     * 根据 S3 对象构建文件节点。
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

    /**
     * 根据对象名构建目录节点。
     *
     * @param objectName 目录前缀
     * @return 树节点
     */
    protected ObjectTreeNode buildTreeNode(String objectName) {
        String n = stripTrailingSlash(objectName);
        return new ObjectTreeNode(Util.getFilename(n), n, domainPrefix + n,
                null, "folder", 0, null);
    }

    private static String stripTrailingSlash(String path) {
        return (path != null && path.endsWith("/")) ? path.substring(0, path.length() - 1) : path;
    }
}


