package com.wiblog.oss.openapi;

import com.wiblog.oss.bean.*;
import com.wiblog.oss.bean.chunk.ChunkMerge;
import com.wiblog.oss.bean.chunk.ChunkPartInfo;
import com.wiblog.oss.bean.chunk.ChunkTask;
import com.wiblog.oss.resp.OssResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

/**
 * OSS OpenAPI 通用文档契约。
 *
 * <p>这里只描述文档元数据，不承载任何控制器运行时逻辑，
 * 由各版本 `openapi-starter` 的控制器实现该接口来选择性启用文档能力。</p>
 *
 * @author panwm
 */
@Tag(name = "OSS 对象存储接口")
public interface CommonOssOpenApiOperations {

    @Operation(summary = "初始化分片上传任务")
    OssResponse<String> initTask(ChunkTask chunkTask);

    @Operation(summary = "合并分片")
    OssResponse<ObjectInfo> merge(ChunkMerge chunkMerge);

    @Operation(summary = "查询已上传的分片列表")
    OssResponse<List<ChunkPartInfo>> listParts(String objectName, String uploadId);

    @Operation(summary = "创建文件夹")
    OssResponse<ObjectInfo> createFolder(String path);

    @Operation(summary = "删除单个文件")
    OssResponse<Void> deleteObject(String objectName);

    @Operation(summary = "批量删除文件")
    OssResponse<Void> deleteObjects(List<String> objectNames);

    @Operation(summary = "删除文件夹（递归）")
    OssResponse<Void> deleteFolder(String path);

    @Operation(summary = "获取文件详情")
    OssResponse<ObjectInfo> getObject(String objectName);

    @Operation(summary = "检查文件是否存在")
    OssResponse<Boolean> objectExists(String objectName);

    @Operation(summary = "列举指定路径下所有对象")
    OssResponse<List<ObjectInfo>> listObjects(String path);

    @Operation(summary = "列举指定路径下一层级")
    OssResponse<List<ObjectTreeNode>> listNextLevel(String path);

    @Operation(summary = "懒加载文件列表（分页）")
    OssResponse<?> lazyList(String path, int maxKeys, String continuationToken);

    @Operation(summary = "获取完整目录树")
    OssResponse<ObjectTreeNode> getObjectTree(String path);

    @Operation(summary = "按关键字搜索并返回目录树")
    OssResponse<ObjectTreeNode> searchObjectTree(String path, String keyword);

    @Operation(summary = "获取文件夹树（仅文件夹节点）")
    OssResponse<List<ObjectTreeNode>> getFolderTree(String path);

    @Operation(summary = "列举所有 Bucket")
    OssResponse<List<BucketInfo>> listBuckets();

    @Operation(summary = "获取指定 Bucket 详情")
    OssResponse<BucketDetailInfo> getBucketDetail(String bucketName);

    @Operation(summary = "获取指定 Bucket ACL")
    OssResponse<BucketAccessInfo> getBucketAccess(String bucketName);

    @Operation(summary = "设置指定 Bucket ACL")
    OssResponse<BucketAccessInfo> setBucketAccess(String bucketName, String acl);

    @Operation(summary = "按时间回滚指定 Bucket")
    OssResponse<BucketRewindResult> rewindBucket(String bucketName, String targetTime);

    @Operation(summary = "测试 OSS 连接")
    OssResponse<Boolean> testConnect();

    @Operation(summary = "复制文件")
    OssResponse<Void> copyObject(String sourceKey, String destKey);

    @Operation(summary = "移动文件")
    OssResponse<Void> moveObject(String sourceKey, String destPath);

    @Operation(summary = "流式解压 ZIP 文件")
    OssResponse<UnzipResult> unzip(String zipObjectKey, String targetPath);

    @Operation(summary = "跨 Bucket 流式解压")
    OssResponse<UnzipResult> unzipCrossBucket(String sourceBucket, String zipObjectKey,
                                              String targetBucket, String targetPath);

    @Operation(summary = "流式解压 ZIP 文件（按路径前缀过滤）")
    OssResponse<UnzipResult> unzipWithFilter(String zipObjectKey, String entryPrefix, String targetPath);

    @Operation(summary = "生成文件下载预签名 URL")
    OssResponse<String> getPresignedUrl(String objectName, long expirationSeconds);

    @Operation(summary = "生成文件上传预签名 URL")
    OssResponse<String> putPresignedUrl(String objectName, String contentType, long expirationSeconds);

    @Operation(summary = "获取对象标签")
    OssResponse<Map<String, String>> getObjectTags(String objectName);

    @Operation(summary = "设置对象标签（覆盖）")
    OssResponse<Void> setObjectTags(String objectName, Map<String, String> tags);

    @Operation(summary = "追加/更新对象标签（合并）")
    OssResponse<Void> mergeObjectTags(String objectName, Map<String, String> tags);

    @Operation(summary = "删除对象的所有标签")
    OssResponse<Void> deleteObjectTags(String objectName);

    @Operation(summary = "创建 Bucket")
    OssResponse<Void> createBucket(String bucketName);

    @Operation(summary = "获取 Bucket 版本控制状态")
    OssResponse<String> getVersioningStatus();

    @Operation(summary = "启用 Bucket 版本控制")
    OssResponse<Void> enableVersioning();

    @Operation(summary = "挂起 Bucket 版本控制")
    OssResponse<Void> suspendVersioning();

    @Operation(summary = "获取 Bucket 生命周期规则")
    OssResponse<List<LifecycleRuleInfo>> getLifecycleRules();

    @Operation(summary = "删除 Bucket 所有生命周期规则")
    OssResponse<Void> deleteLifecycleRules();

    @Operation(summary = "添加文件过期删除规则")
    OssResponse<Void> addExpirationRule(String ruleId, String prefix, int expirationDays);

    @Operation(summary = "获取 Bucket CORS 配置")
    OssResponse<List<CorsRuleInfo>> getCorsRules();

    @Operation(summary = "设置允许所有来源的 CORS")
    OssResponse<Void> allowAllOriginsCors();

    @Operation(summary = "删除 Bucket CORS 配置")
    OssResponse<Void> deleteCorsRules();

    @Operation(summary = "获取 Bucket 访问策略")
    OssResponse<String> getBucketPolicy();

    @Operation(summary = "设置 Bucket 访问策略")
    OssResponse<Void> putBucketPolicy(String policyJson);

    @Operation(summary = "删除 Bucket 访问策略")
    OssResponse<Void> deleteBucketPolicy();

    @Operation(summary = "启用 Bucket 服务端加密")
    OssResponse<Void> enableEncryption();

    @Operation(summary = "开启 Bucket 公共访问屏蔽")
    OssResponse<Void> blockAllPublicAccess();

    @Operation(summary = "获取 Bucket 标签")
    OssResponse<Map<String, String>> getBucketTags();

    @Operation(summary = "设置 Bucket 标签（覆盖）")
    OssResponse<Void> setBucketTags(Map<String, String> tags);

    @Operation(summary = "删除 Bucket 所有标签")
    OssResponse<Void> deleteBucketTags();
}
