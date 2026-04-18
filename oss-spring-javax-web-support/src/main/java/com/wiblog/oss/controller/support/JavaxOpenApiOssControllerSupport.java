package com.wiblog.oss.controller.support;

import com.wiblog.oss.bean.*;
import com.wiblog.oss.bean.chunk.ChunkMerge;
import com.wiblog.oss.bean.chunk.ChunkPartInfo;
import com.wiblog.oss.bean.chunk.ChunkTarget;
import com.wiblog.oss.bean.chunk.ChunkTask;
import com.wiblog.oss.resp.OssResponse;
import com.wiblog.oss.service.OssTemplate;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * `javax.servlet` 体系共享的 OpenAPI HTTP 端点实现。
 *
 * <p>公共文档元数据与请求映射统一沉到该基类，
 * 版本 Starter 只保留公开控制器类型与 Bean 注册逻辑，
 * 以避免 Boot2 OpenAPI Starter 再维护一整份重复控制器实现。</p>
 *
 * @author panwm
 */
public abstract class JavaxOpenApiOssControllerSupport extends JavaxOssControllerSupport {

    public JavaxOpenApiOssControllerSupport(OssTemplate ossTemplate) {
        super(ossTemplate);
    }

    @Override
    @Operation(summary = "初始化分片上传任务")
    @PostMapping("/multipart/init")
    public OssResponse<String> initTask(ChunkTask chunkTask) {
        return super.initTask(chunkTask);
    }

    @Override
    @Operation(summary = "上传文件分片")
    @PostMapping("/multipart/chunk")
    public OssResponse<ChunkTarget> chunk(
            @RequestParam Integer chunkNumber,
            @RequestParam String filename,
            @RequestParam String path,
            @RequestParam String guid,
            @RequestParam MultipartFile file,
            @RequestParam String uploadId) {
        return super.chunk(chunkNumber, filename, path, guid, file, uploadId);
    }

    @Override
    @Operation(summary = "合并分片")
    @PostMapping("/multipart/merge")
    public OssResponse<ObjectInfo> merge(@RequestBody ChunkMerge chunkMerge) {
        return super.merge(chunkMerge);
    }

    @Override
    @Operation(summary = "查询已上传的分片列表")
    @GetMapping("/multipart/parts")
    public OssResponse<List<ChunkPartInfo>> listParts(
            @RequestParam String objectName,
            @RequestParam String uploadId) {
        return super.listParts(objectName, uploadId);
    }

    @Override
    @Operation(summary = "上传文件")
    @PostMapping("/object")
    public OssResponse<ObjectInfo> uploadObject(
            @RequestParam MultipartFile file,
            @RequestParam String path,
            @RequestParam(required = false) String filename) throws IOException {
        return super.uploadObject(file, path, filename);
    }

    @Override
    @Operation(summary = "创建文件夹")
    @PostMapping("/folder")
    public OssResponse<ObjectInfo> createFolder(@RequestParam String path) {
        return super.createFolder(path);
    }

    @Override
    @Operation(summary = "删除单个文件")
    @DeleteMapping("/object")
    public OssResponse<Void> deleteObject(@RequestParam String objectName) {
        return super.deleteObject(objectName);
    }

    @Override
    @Operation(summary = "批量删除文件")
    @DeleteMapping("/objects")
    public OssResponse<Void> deleteObjects(@RequestBody List<String> objectNames) {
        return super.deleteObjects(objectNames);
    }

    @Override
    @Operation(summary = "删除文件夹（递归）")
    @DeleteMapping("/folder")
    public OssResponse<Void> deleteFolder(@RequestParam String path) {
        return super.deleteFolder(path);
    }

    @Override
    @Operation(summary = "获取文件详情")
    @GetMapping("/object")
    public OssResponse<ObjectInfo> getObject(@RequestParam String objectName) {
        return super.getObject(objectName);
    }

    @Override
    @Operation(summary = "检查文件是否存在")
    @GetMapping("/object/exists")
    public OssResponse<Boolean> objectExists(@RequestParam String objectName) {
        return super.objectExists(objectName);
    }

    @Override
    @Operation(summary = "列举指定路径下所有对象")
    @GetMapping("/object/list")
    public OssResponse<List<ObjectInfo>> listObjects(@RequestParam String path) {
        return super.listObjects(path);
    }

    @Override
    @Operation(summary = "列举指定路径下一层级")
    @GetMapping("/object/list/next-level")
    public OssResponse<List<ObjectTreeNode>> listNextLevel(@RequestParam String path) {
        return super.listNextLevel(path);
    }

    @Override
    @Operation(summary = "懒加载文件列表（分页）")
    @GetMapping("/object/list/lazy")
    public OssResponse<?> lazyList(
            @RequestParam String path,
            @RequestParam(defaultValue = "100") int maxKeys,
            @RequestParam(required = false) String continuationToken) {
        return super.lazyList(path, maxKeys, continuationToken);
    }

    @Override
    @Operation(summary = "获取完整目录树")
    @GetMapping("/object/tree")
    public OssResponse<ObjectTreeNode> getObjectTree(@RequestParam String path) {
        return super.getObjectTree(path);
    }

    @Override
    @Operation(summary = "按关键字搜索并返回目录树")
    @GetMapping("/object/tree/search")
    public OssResponse<ObjectTreeNode> searchObjectTree(
            @RequestParam String path,
            @RequestParam String keyword) {
        return super.searchObjectTree(path, keyword);
    }

    @Override
    @Operation(summary = "获取文件夹树（仅文件夹节点）")
    @GetMapping("/object/tree/folder")
    public OssResponse<List<ObjectTreeNode>> getFolderTree(@RequestParam String path) {
        return super.getFolderTree(path);
    }

    @Override
    @Operation(summary = "列举所有 Bucket")
    @GetMapping("/buckets")
    public OssResponse<List<BucketInfo>> listBuckets() {
        return super.listBuckets();
    }

    @Override
    @Operation(summary = "获取指定 Bucket 详情")
    @GetMapping("/buckets/{bucketName}")
    public OssResponse<BucketDetailInfo> getBucketDetail(@PathVariable String bucketName) {
        return super.getBucketDetail(bucketName);
    }

    @Override
    @Operation(summary = "获取指定 Bucket ACL")
    @GetMapping("/buckets/{bucketName}/access")
    public OssResponse<BucketAccessInfo> getBucketAccess(@PathVariable String bucketName) {
        return super.getBucketAccess(bucketName);
    }

    @Override
    @Operation(summary = "设置指定 Bucket ACL")
    @PutMapping("/buckets/{bucketName}/access")
    public OssResponse<BucketAccessInfo> setBucketAccess(
            @PathVariable String bucketName,
            @RequestParam String acl) {
        return super.setBucketAccess(bucketName, acl);
    }

    @Override
    @Operation(summary = "按时间回滚指定 Bucket")
    @PostMapping("/buckets/{bucketName}/rewind")
    public OssResponse<BucketRewindResult> rewindBucket(
            @PathVariable String bucketName,
            @RequestParam String targetTime) {
        return super.rewindBucket(bucketName, targetTime);
    }

    @Override
    @Operation(summary = "测试 OSS 连接")
    @GetMapping("/connect")
    public OssResponse<Boolean> testConnect() {
        return super.testConnect();
    }

    @Override
    @Operation(summary = "预览文件")
    @GetMapping("/object/preview/**")
    public void previewObject(HttpServletResponse response, HttpServletRequest request) throws IOException {
        super.previewObject(response, request);
    }

    @Override
    @Operation(summary = "下载文件")
    @GetMapping("/object/download/**")
    public void downloadObject(HttpServletResponse response, HttpServletRequest request) throws IOException {
        super.downloadObject(response, request);
    }

    @Override
    @Operation(summary = "按路径前缀压缩下载 ZIP")
    @GetMapping("/folder/download")
    public void downloadFolderAsZip(
            @RequestParam String path,
            @RequestParam(required = false) String filename,
            HttpServletResponse response) throws IOException {
        super.downloadFolderAsZip(path, filename, response);
    }

    @Override
    @Operation(summary = "复制文件")
    @PostMapping("/object/copy")
    public OssResponse<Void> copyObject(
            @RequestParam String sourceKey,
            @RequestParam String destKey) {
        return super.copyObject(sourceKey, destKey);
    }

    @Override
    @Operation(summary = "移动文件")
    @PostMapping("/object/move")
    public OssResponse<Void> moveObject(
            @RequestParam String sourceKey,
            @RequestParam String destPath) {
        return super.moveObject(sourceKey, destPath);
    }

    @Override
    @Operation(summary = "流式解压 ZIP 文件")
    @PostMapping("/unzip")
    public OssResponse<UnzipResult> unzip(
            @RequestParam String zipObjectKey,
            @RequestParam String targetPath) {
        return super.unzip(zipObjectKey, targetPath);
    }

    @Override
    @Operation(summary = "跨 Bucket 流式解压")
    @PostMapping("/unzip/cross-bucket")
    public OssResponse<UnzipResult> unzipCrossBucket(
            @RequestParam String sourceBucket,
            @RequestParam String zipObjectKey,
            @RequestParam String targetBucket,
            @RequestParam String targetPath) {
        return super.unzipCrossBucket(sourceBucket, zipObjectKey, targetBucket, targetPath);
    }

    @Override
    @Operation(summary = "流式解压 ZIP 文件（按路径前缀过滤）")
    @PostMapping("/unzip/filter")
    public OssResponse<UnzipResult> unzipWithFilter(
            @RequestParam String zipObjectKey,
            @RequestParam(required = false) String entryPrefix,
            @RequestParam String targetPath) {
        return super.unzipWithFilter(zipObjectKey, entryPrefix, targetPath);
    }

    @Override
    @Operation(summary = "生成文件下载预签名 URL")
    @GetMapping("/presign/get")
    public OssResponse<String> getPresignedUrl(
            @RequestParam String objectName,
            @RequestParam(defaultValue = "3600") long expirationSeconds) {
        return super.getPresignedUrl(objectName, expirationSeconds);
    }

    @Override
    @Operation(summary = "生成文件上传预签名 URL")
    @GetMapping("/presign/put")
    public OssResponse<String> putPresignedUrl(
            @RequestParam String objectName,
            @RequestParam(defaultValue = "application/octet-stream") String contentType,
            @RequestParam(defaultValue = "3600") long expirationSeconds) {
        return super.putPresignedUrl(objectName, contentType, expirationSeconds);
    }

    @Override
    @Operation(summary = "获取对象标签")
    @GetMapping("/object/tags")
    public OssResponse<Map<String, String>> getObjectTags(@RequestParam String objectName) {
        return super.getObjectTags(objectName);
    }

    @Override
    @Operation(summary = "设置对象标签（覆盖）")
    @PutMapping("/object/tags")
    public OssResponse<Void> setObjectTags(
            @RequestParam String objectName,
            @RequestBody Map<String, String> tags) {
        return super.setObjectTags(objectName, tags);
    }

    @Override
    @Operation(summary = "追加/更新对象标签（合并）")
    @PatchMapping("/object/tags")
    public OssResponse<Void> mergeObjectTags(
            @RequestParam String objectName,
            @RequestBody Map<String, String> tags) {
        return super.mergeObjectTags(objectName, tags);
    }

    @Override
    @Operation(summary = "删除对象的所有标签")
    @DeleteMapping("/object/tags")
    public OssResponse<Void> deleteObjectTags(@RequestParam String objectName) {
        return super.deleteObjectTags(objectName);
    }

    @Override
    @Operation(summary = "创建 Bucket")
    @PostMapping("/bucket")
    public OssResponse<Void> createBucket(@RequestParam String bucketName) {
        return super.createBucket(bucketName);
    }

    @Override
    @Operation(summary = "获取 Bucket 版本控制状态")
    @GetMapping("/bucket/versioning")
    public OssResponse<String> getVersioningStatus() {
        return super.getVersioningStatus();
    }

    @Override
    @Operation(summary = "启用 Bucket 版本控制")
    @PutMapping("/bucket/versioning/enable")
    public OssResponse<Void> enableVersioning() {
        return super.enableVersioning();
    }

    @Override
    @Operation(summary = "挂起 Bucket 版本控制")
    @PutMapping("/bucket/versioning/suspend")
    public OssResponse<Void> suspendVersioning() {
        return super.suspendVersioning();
    }

    @Override
    @Operation(summary = "获取 Bucket 生命周期规则")
    @GetMapping("/bucket/lifecycle")
    public OssResponse<List<LifecycleRuleInfo>> getLifecycleRules() {
        return super.getLifecycleRules();
    }

    @Override
    @Operation(summary = "删除 Bucket 所有生命周期规则")
    @DeleteMapping("/bucket/lifecycle")
    public OssResponse<Void> deleteLifecycleRules() {
        return super.deleteLifecycleRules();
    }

    @Override
    @Operation(summary = "添加文件过期删除规则")
    @PostMapping("/bucket/lifecycle/expiration")
    public OssResponse<Void> addExpirationRule(
            @RequestParam String ruleId,
            @RequestParam(defaultValue = "") String prefix,
            @RequestParam int expirationDays) {
        return super.addExpirationRule(ruleId, prefix, expirationDays);
    }

    @Override
    @Operation(summary = "获取 Bucket CORS 配置")
    @GetMapping("/bucket/cors")
    public OssResponse<List<CorsRuleInfo>> getCorsRules() {
        return super.getCorsRules();
    }

    @Override
    @Operation(summary = "设置允许所有来源的 CORS")
    @PutMapping("/bucket/cors/allow-all")
    public OssResponse<Void> allowAllOriginsCors() {
        return super.allowAllOriginsCors();
    }

    @Override
    @Operation(summary = "删除 Bucket CORS 配置")
    @DeleteMapping("/bucket/cors")
    public OssResponse<Void> deleteCorsRules() {
        return super.deleteCorsRules();
    }

    @Override
    @Operation(summary = "获取 Bucket 访问策略")
    @GetMapping("/bucket/policy")
    public OssResponse<String> getBucketPolicy() {
        return super.getBucketPolicy();
    }

    @Override
    @Operation(summary = "设置 Bucket 访问策略")
    @PutMapping("/bucket/policy")
    public OssResponse<Void> putBucketPolicy(@RequestBody String policyJson) {
        return super.putBucketPolicy(policyJson);
    }

    @Override
    @Operation(summary = "删除 Bucket 访问策略")
    @DeleteMapping("/bucket/policy")
    public OssResponse<Void> deleteBucketPolicy() {
        return super.deleteBucketPolicy();
    }

    @Override
    @Operation(summary = "启用 Bucket 服务端加密")
    @PutMapping("/bucket/encryption/enable")
    public OssResponse<Void> enableEncryption() {
        return super.enableEncryption();
    }

    @Override
    @Operation(summary = "开启 Bucket 公共访问屏蔽")
    @PutMapping("/bucket/public-access/block")
    public OssResponse<Void> blockAllPublicAccess() {
        return super.blockAllPublicAccess();
    }

    @Override
    @Operation(summary = "获取 Bucket 标签")
    @GetMapping("/bucket/tags")
    public OssResponse<Map<String, String>> getBucketTags() {
        return super.getBucketTags();
    }

    @Override
    @Operation(summary = "设置 Bucket 标签（覆盖）")
    @PutMapping("/bucket/tags")
    public OssResponse<Void> setBucketTags(@RequestBody Map<String, String> tags) {
        return super.setBucketTags(tags);
    }

    @Override
    @Operation(summary = "删除 Bucket 所有标签")
    @DeleteMapping("/bucket/tags")
    public OssResponse<Void> deleteBucketTags() {
        return super.deleteBucketTags();
    }
}
