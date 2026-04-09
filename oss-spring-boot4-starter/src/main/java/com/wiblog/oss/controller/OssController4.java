package com.wiblog.oss.controller;

import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;
import com.wiblog.oss.bean.UnzipResult;
import com.wiblog.oss.bean.chunk.Chunk;
import com.wiblog.oss.bean.chunk.ChunkMerge;
import com.wiblog.oss.bean.chunk.ChunkTarget;
import com.wiblog.oss.bean.chunk.ChunkTask;
import com.wiblog.oss.resp.R;
import com.wiblog.oss.service.JakartaOssPreviewContext;
import com.wiblog.oss.service.OssTemplate;
import com.wiblog.oss.util.Util;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.util.AntPathMatcher;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Spring Boot 4.x OSS HTTP 端点（jakarta.servlet）。
 *
 * @author panwm
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("${oss.http.prefix:}/oss")
@Tag(name = "OSS 对象存储接口")
public class OssController4 {

    private final OssTemplate ossTemplate;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    // ================================================================
    // 分片上传
    // ================================================================

    @PostMapping("/multipart/init")
    @Operation(summary = "初始化分片上传任务")
    public R<String> initTask(@Validated ChunkTask chunkTask) {
        return R.data(ossTemplate.put().initTask(chunkTask));
    }

    @PostMapping("/multipart/chunk")
    @Operation(summary = "上传文件分片")
    public R<ChunkTarget> chunk(@Validated Chunk chunk) {
        return R.data(ossTemplate.put().chunk(chunk));
    }

    @PostMapping("/multipart/merge")
    @Operation(summary = "合并分片")
    public R<ObjectInfo> merge(@Validated ChunkMerge chunkMerge) {
        return R.data(ossTemplate.put().merge(chunkMerge));
    }

    @GetMapping("/multipart/parts")
    @Operation(summary = "查询已上传的分片列表")
    public R<List<?>> listParts(
            @NotBlank @RequestParam String objectName,
            @NotBlank @RequestParam String uploadId) {
        return R.data(ossTemplate.put().listParts(
                ossTemplate.query().getOssProperties().getBucketName(), objectName, uploadId));
    }

    // ================================================================
    // 文件上传
    // ================================================================

    @PostMapping("/object")
    @Operation(summary = "上传文件")
    public R<ObjectInfo> uploadObject(
            @NotNull @RequestParam("file") MultipartFile file,
            @NotBlank @RequestParam String path,
            @RequestParam(required = false) String filename) throws IOException {
        InputStream in = file.getInputStream();
        String name = Util.isBlank(filename) ? file.getOriginalFilename() : filename;
        return R.data(ossTemplate.put().putObject(path, name, in));
    }

    @PostMapping("/folder")
    @Operation(summary = "创建文件夹")
    public R<ObjectInfo> createFolder(@NotBlank @RequestParam String path) {
        return R.data(ossTemplate.put().mkdirs(path));
    }

    // ================================================================
    // 文件删除
    // ================================================================

    @DeleteMapping("/object")
    @Operation(summary = "删除单个文件")
    public R<Void> deleteObject(@NotBlank @RequestParam String objectName) {
        ossTemplate.delete().removeObject(objectName);
        return R.success("删除成功");
    }

    @DeleteMapping("/objects")
    @Operation(summary = "批量删除文件")
    public R<Void> deleteObjects(@RequestBody @NotNull List<String> objectNames) {
        objectNames.forEach(key -> ossTemplate.delete().removeObject(key));
        return R.success("批量删除成功");
    }

    @DeleteMapping("/folder")
    @Operation(summary = "删除文件夹（递归）")
    public R<Void> deleteFolder(@NotBlank @RequestParam String path) {
        ossTemplate.delete().removeFolder(path);
        return R.success("删除成功");
    }

    // ================================================================
    // 文件查询
    // ================================================================

    @GetMapping("/object")
    @Operation(summary = "获取文件详情")
    public R<ObjectInfo> getObject(@NotBlank @RequestParam String objectName) {
        return R.data(ossTemplate.query().getObjectInfo(objectName));
    }

    @GetMapping("/object/exists")
    @Operation(summary = "检查文件是否存在")
    public R<Boolean> objectExists(@NotBlank @RequestParam String objectName) {
        return R.data(ossTemplate.query().checkExist(objectName));
    }

    @GetMapping("/object/list")
    @Operation(summary = "列举指定路径下所有对象")
    public R<List<ObjectInfo>> listObjects(@NotBlank @RequestParam String path) {
        return R.data(ossTemplate.query().listObjects(path));
    }

    @GetMapping("/object/list/next-level")
    @Operation(summary = "列举指定路径下一层级")
    public R<List<ObjectTreeNode>> listNextLevel(@NotBlank @RequestParam String path) {
        return R.data(ossTemplate.query().listNextLevel(path));
    }

    @GetMapping("/object/list/lazy")
    @Operation(summary = "懒加载文件列表（分页）")
    public R<?> lazyList(
            @NotBlank @RequestParam String path,
            @RequestParam(defaultValue = "100") @Min(1) int maxKeys,
            @RequestParam(required = false) String continuationToken) {
        return R.data(ossTemplate.query().lazyList(path, maxKeys, continuationToken));
    }

    @GetMapping("/object/tree")
    @Operation(summary = "获取完整目录树")
    public R<ObjectTreeNode> getObjectTree(@NotBlank @RequestParam String path) {
        return R.data(ossTemplate.query().getTreeList(path));
    }

    @GetMapping("/object/tree/search")
    @Operation(summary = "按关键字搜索并返回目录树")
    public R<ObjectTreeNode> searchObjectTree(
            @NotBlank @RequestParam String path,
            @NotBlank @RequestParam String keyword) {
        return R.data(ossTemplate.query().getTreeListByName(path, keyword));
    }

    @GetMapping("/object/tree/folder")
    @Operation(summary = "获取文件夹树（仅文件夹节点）")
    public R<List<ObjectTreeNode>> getFolderTree(@NotBlank @RequestParam String path) {
        return R.data(ossTemplate.query().getFolderTreeList(path));
    }

    @GetMapping("/buckets")
    @Operation(summary = "列举所有 Bucket")
    public R<List<?>> listBuckets() {
        return R.data(ossTemplate.query().getAllBuckets());
    }

    @GetMapping("/connect")
    @Operation(summary = "测试 OSS 连接")
    public R<Boolean> testConnect() {
        return R.data(ossTemplate.query().testConnect());
    }

    // ================================================================
    // 文件预览 / 下载
    // ================================================================

    @GetMapping("/object/preview/**")
    @Operation(summary = "预览文件")
    public void previewObject(HttpServletResponse response, HttpServletRequest request)
            throws IOException {
        ossTemplate.query().previewObject(
                new JakartaOssPreviewContext(request, response),
                extractObjectName(request), false);
    }

    @GetMapping("/object/download/**")
    @Operation(summary = "下载文件")
    public void downloadObject(HttpServletResponse response, HttpServletRequest request)
            throws IOException {
        ossTemplate.query().previewObject(
                new JakartaOssPreviewContext(request, response),
                extractObjectName(request), true);
    }

    // ================================================================
    // 文件复制 / 移动
    // ================================================================

    @PostMapping("/object/copy")
    @Operation(summary = "复制文件")
    public R<Void> copyObject(
            @NotBlank @RequestParam String sourceKey,
            @NotBlank @RequestParam String destKey) {
        ossTemplate.put().copyFile(sourceKey, destKey);
        return R.success("复制成功");
    }

    @PostMapping("/object/move")
    @Operation(summary = "移动文件")
    public R<Void> moveObject(
            @NotBlank @RequestParam String sourceKey,
            @NotBlank @RequestParam String destPath) {
        ossTemplate.put().move(sourceKey, destPath);
        return R.success("移动成功");
    }

    // ================================================================
    // 流式解压
    // ================================================================

    @PostMapping("/unzip")
    @Operation(summary = "流式解压 ZIP 文件")
    public R<UnzipResult> unzip(
            @NotBlank @RequestParam String zipObjectKey,
            @NotBlank @RequestParam String targetPath) {
        return R.data(ossTemplate.unzip().unzip(zipObjectKey, targetPath));
    }

    @PostMapping("/unzip/cross-bucket")
    @Operation(summary = "跨 Bucket 流式解压")
    public R<UnzipResult> unzipCrossBucket(
            @NotBlank @RequestParam String sourceBucket,
            @NotBlank @RequestParam String zipObjectKey,
            @NotBlank @RequestParam String targetBucket,
            @NotBlank @RequestParam String targetPath) {
        return R.data(ossTemplate.unzip().unzip(sourceBucket, zipObjectKey, targetBucket, targetPath));
    }

    @PostMapping("/unzip/filter")
    @Operation(summary = "流式解压 ZIP 文件（按路径前缀过滤）")
    public R<UnzipResult> unzipWithFilter(
            @NotBlank @RequestParam String zipObjectKey,
            @RequestParam(required = false) String entryPrefix,
            @NotBlank @RequestParam String targetPath) {
        return R.data(ossTemplate.unzip().unzipWithFilter(zipObjectKey, entryPrefix, targetPath));
    }

    // ================================================================
    // 预签名 URL
    // ================================================================

    @GetMapping("/presign/get")
    @Operation(summary = "生成文件下载预签名 URL")
    public R<String> getPresignedUrl(
            @NotBlank @RequestParam String objectName,
            @RequestParam(defaultValue = "3600") @Min(1) long expirationSeconds) {
        return R.data(ossTemplate.presign()
                .generateGetPresignedUrl(objectName, Duration.ofSeconds(expirationSeconds)));
    }

    @GetMapping("/presign/put")
    @Operation(summary = "生成文件上传预签名 URL")
    public R<String> putPresignedUrl(
            @NotBlank @RequestParam String objectName,
            @RequestParam(defaultValue = "application/octet-stream") String contentType,
            @RequestParam(defaultValue = "3600") @Min(1) long expirationSeconds) {
        return R.data(ossTemplate.presign().generatePutPresignedUrl(
                objectName, contentType, Duration.ofSeconds(expirationSeconds), null));
    }

    // ================================================================
    // 对象标签
    // ================================================================

    @GetMapping("/object/tags")
    @Operation(summary = "获取对象标签")
    public R<Map<String, String>> getObjectTags(@NotBlank @RequestParam String objectName) {
        return R.data(ossTemplate.tagging().getObjectTags(objectName));
    }

    @PutMapping("/object/tags")
    @Operation(summary = "设置对象标签（覆盖）")
    public R<Void> setObjectTags(
            @NotBlank @RequestParam String objectName,
            @RequestBody @NotNull Map<String, String> tags) {
        ossTemplate.tagging().setObjectTags(objectName, tags);
        return R.success("标签设置成功");
    }

    @PatchMapping("/object/tags")
    @Operation(summary = "追加/更新对象标签（合并）")
    public R<Void> mergeObjectTags(
            @NotBlank @RequestParam String objectName,
            @RequestBody @NotNull Map<String, String> tags) {
        ossTemplate.tagging().mergeObjectTags(objectName, tags);
        return R.success("标签更新成功");
    }

    @DeleteMapping("/object/tags")
    @Operation(summary = "删除对象的所有标签")
    public R<Void> deleteObjectTags(@NotBlank @RequestParam String objectName) {
        ossTemplate.tagging().deleteObjectTags(objectName);
        return R.success("标签删除成功");
    }

    // ================================================================
    // Bucket 管理
    // ================================================================

    @PostMapping("/bucket")
    @Operation(summary = "创建 Bucket")
    public R<Void> createBucket(@NotBlank @RequestParam String bucketName) {
        ossTemplate.put().createBucket(bucketName);
        return R.success("Bucket 创建成功");
    }

    @GetMapping("/bucket/versioning")
    @Operation(summary = "获取 Bucket 版本控制状态")
    public R<String> getVersioningStatus() {
        return R.data(ossTemplate.bucket().getVersioningStatus());
    }

    @PutMapping("/bucket/versioning/enable")
    @Operation(summary = "启用 Bucket 版本控制")
    public R<Void> enableVersioning() {
        ossTemplate.bucket().enableVersioning();
        return R.success("版本控制已启用");
    }

    @PutMapping("/bucket/versioning/suspend")
    @Operation(summary = "挂起 Bucket 版本控制")
    public R<Void> suspendVersioning() {
        ossTemplate.bucket().suspendVersioning();
        return R.success("版本控制已挂起");
    }

    @GetMapping("/bucket/lifecycle")
    @Operation(summary = "获取 Bucket 生命周期规则")
    public R<List<?>> getLifecycleRules() {
        return R.data(ossTemplate.bucket().getLifecycleRules());
    }

    @DeleteMapping("/bucket/lifecycle")
    @Operation(summary = "删除 Bucket 所有生命周期规则")
    public R<Void> deleteLifecycleRules() {
        ossTemplate.bucket().deleteLifecycleRules();
        return R.success("生命周期规则已删除");
    }

    @PostMapping("/bucket/lifecycle/expiration")
    @Operation(summary = "添加文件过期删除规则")
    public R<Void> addExpirationRule(
            @NotBlank @RequestParam String ruleId,
            @RequestParam(defaultValue = "") String prefix,
            @RequestParam @Min(1) int expirationDays) {
        ossTemplate.bucket().addExpirationRule(ruleId, prefix, expirationDays);
        return R.success("过期规则添加成功");
    }

    @GetMapping("/bucket/cors")
    @Operation(summary = "获取 Bucket CORS 配置")
    public R<List<?>> getCorsRules() {
        return R.data(ossTemplate.bucket().getCorsRules());
    }

    @PutMapping("/bucket/cors/allow-all")
    @Operation(summary = "设置允许所有来源的 CORS")
    public R<Void> allowAllOriginsCors() {
        ossTemplate.bucket().allowAllOriginsCors();
        return R.success("CORS 配置成功");
    }

    @DeleteMapping("/bucket/cors")
    @Operation(summary = "删除 Bucket CORS 配置")
    public R<Void> deleteCorsRules() {
        ossTemplate.bucket().deleteCorsRules();
        return R.success("CORS 配置已删除");
    }

    @GetMapping("/bucket/policy")
    @Operation(summary = "获取 Bucket 访问策略")
    public R<String> getBucketPolicy() {
        return R.data(ossTemplate.bucket().getBucketPolicy());
    }

    @PutMapping("/bucket/policy")
    @Operation(summary = "设置 Bucket 访问策略")
    public R<Void> putBucketPolicy(@RequestBody @NotBlank String policyJson) {
        ossTemplate.bucket().putBucketPolicy(policyJson);
        return R.success("策略设置成功");
    }

    @DeleteMapping("/bucket/policy")
    @Operation(summary = "删除 Bucket 访问策略")
    public R<Void> deleteBucketPolicy() {
        ossTemplate.bucket().deleteBucketPolicy();
        return R.success("策略已删除");
    }

    @PutMapping("/bucket/encryption/enable")
    @Operation(summary = "启用 Bucket 服务端加密")
    public R<Void> enableEncryption() {
        ossTemplate.bucket().enableServerSideEncryption();
        return R.success("加密已启用");
    }

    @PutMapping("/bucket/public-access/block")
    @Operation(summary = "开启 Bucket 公共访问屏蔽")
    public R<Void> blockAllPublicAccess() {
        ossTemplate.bucket().blockAllPublicAccess();
        return R.success("公共访问已屏蔽");
    }

    @GetMapping("/bucket/tags")
    @Operation(summary = "获取 Bucket 标签")
    public R<Map<String, String>> getBucketTags() {
        return R.data(ossTemplate.tagging().getBucketTags());
    }

    @PutMapping("/bucket/tags")
    @Operation(summary = "设置 Bucket 标签（覆盖）")
    public R<Void> setBucketTags(@RequestBody @NotNull Map<String, String> tags) {
        ossTemplate.tagging().setBucketTags(tags);
        return R.success("Bucket 标签设置成功");
    }

    @DeleteMapping("/bucket/tags")
    @Operation(summary = "删除 Bucket 所有标签")
    public R<Void> deleteBucketTags() {
        ossTemplate.tagging().deleteBucketTags();
        return R.success("Bucket 标签已删除");
    }

    // ================================================================
    // 私有工具
    // ================================================================

    private String extractObjectName(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return antPathMatcher.extractPathWithinPattern(pattern, path);
    }
}
