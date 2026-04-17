package com.wiblog.oss.controller;

import com.wiblog.oss.bean.*;
import com.wiblog.oss.bean.chunk.Chunk;
import com.wiblog.oss.bean.chunk.ChunkMerge;
import com.wiblog.oss.bean.chunk.ChunkTarget;
import com.wiblog.oss.bean.chunk.ChunkTask;
import com.wiblog.oss.resp.OssResponse;
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
 * Spring Boot 3.x OSS HTTP 端点（jakarta.servlet）。
 *
 * @author panwm
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("${oss.http.prefix:}/oss")
@Tag(name = "OSS 对象存储接口")
public class OssController3 {

    private final OssTemplate ossTemplate;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    // ================================================================
    // 分片上传
    // ================================================================

    /**
     * HTTP 端点：初始化分片上传任务。
     */
    @PostMapping("/multipart/init")
    @Operation(summary = "初始化分片上传任务")
    public OssResponse<String> initTask(@Validated ChunkTask chunkTask) {
        return OssResponse.data(ossTemplate.put().initTask(chunkTask));
    }

    /**
     * HTTP 端点：上传单个文件分片。
     */
    @PostMapping("/multipart/chunk")
    @Operation(summary = "上传文件分片")
    public OssResponse<ChunkTarget> chunk(@Validated Chunk chunk) {
        return OssResponse.data(ossTemplate.put().chunk(chunk));
    }

    /**
     * HTTP 端点：合并已上传的所有分片。
     */
    @PostMapping("/multipart/merge")
    @Operation(summary = "合并分片")
    public OssResponse<ObjectInfo> merge(@RequestBody @Validated ChunkMerge chunkMerge) {
        return OssResponse.data(ossTemplate.put().merge(chunkMerge));
    }

    /**
     * HTTP 端点：查询分片上传任务的已上传分片列表。
     */
    @GetMapping("/multipart/parts")
    @Operation(summary = "查询已上传的分片列表")
    public OssResponse<List<?>> listParts(
            @NotBlank @RequestParam String objectName,
            @NotBlank @RequestParam String uploadId) {
        return OssResponse.data(ossTemplate.put().listParts(
                ossTemplate.query().getOssProperties().getBucketName(), objectName, uploadId));
    }

    // ================================================================
    // 文件上传
    // ================================================================

    /**
     * HTTP 端点：上传文件到对象存储。
     *
     * <p>控制器只负责解析 `MultipartFile` 与回填文件名，
     * 实际上传逻辑交由 {@link OssTemplate#put()} 门面处理。</p>
     */
    @PostMapping("/object")
    @Operation(summary = "上传文件")
    public OssResponse<ObjectInfo> uploadObject(
            @NotNull @RequestParam("file") MultipartFile file,
            @NotBlank @RequestParam String path,
            @RequestParam(required = false) String filename) throws IOException {
        InputStream in = file.getInputStream();
        String name = Util.isBlank(filename) ? file.getOriginalFilename() : filename;
        return OssResponse.data(ossTemplate.put().putObject(path, name, in));
    }

    /**
     * HTTP 端点：创建目录占位对象。
     */
    @PostMapping("/folder")
    @Operation(summary = "创建文件夹")
    public OssResponse<ObjectInfo> createFolder(@NotBlank @RequestParam String path) {
        return OssResponse.data(ossTemplate.put().mkdirs(path));
    }

    // ================================================================
    // 文件删除
    // ================================================================

    /**
     * HTTP 端点：删除单个对象。
     */
    @DeleteMapping("/object")
    @Operation(summary = "删除单个文件")
    public OssResponse<Void> deleteObject(@NotBlank @RequestParam String objectName) {
        ossTemplate.delete().removeObject(objectName);
        return OssResponse.success("删除成功");
    }

    /**
     * HTTP 端点：批量删除多个对象或目录。
     */
    @DeleteMapping("/objects")
    @Operation(summary = "批量删除文件")
    public OssResponse<Void> deleteObjects(@RequestBody @NotNull List<String> objectNames) {
        ossTemplate.delete().removeObjects(objectNames);
        return OssResponse.success("批量删除成功");
    }

    /**
     * HTTP 端点：递归删除目录下全部对象。
     */
    @DeleteMapping("/folder")
    @Operation(summary = "删除文件夹（递归）")
    public OssResponse<Void> deleteFolder(@NotBlank @RequestParam String path) {
        ossTemplate.delete().removeFolder(path);
        return OssResponse.success("删除成功");
    }

    // ================================================================
    // 文件查询
    // ================================================================

    /**
     * HTTP 端点：查询单个对象详情。
     */
    @GetMapping("/object")
    @Operation(summary = "获取文件详情")
    public OssResponse<ObjectInfo> getObject(@NotBlank @RequestParam String objectName) {
        return OssResponse.data(ossTemplate.query().getObjectInfo(objectName));
    }

    /**
     * HTTP 端点：检查对象是否存在。
     */
    @GetMapping("/object/exists")
    @Operation(summary = "检查文件是否存在")
    public OssResponse<Boolean> objectExists(@NotBlank @RequestParam String objectName) {
        return OssResponse.data(ossTemplate.query().checkExist(objectName));
    }

    /**
     * HTTP 端点：列举指定前缀下的全部对象。
     */
    @GetMapping("/object/list")
    @Operation(summary = "列举指定路径下所有对象")
    public OssResponse<List<ObjectInfo>> listObjects(@NotBlank @RequestParam String path) {
        return OssResponse.data(ossTemplate.query().listObjects(path));
    }

    /**
     * HTTP 端点：列举指定前缀的下一层级节点。
     */
    @GetMapping("/object/list/next-level")
    @Operation(summary = "列举指定路径下一层级")
    public OssResponse<List<ObjectTreeNode>> listNextLevel(@NotBlank @RequestParam String path) {
        return OssResponse.data(ossTemplate.query().listNextLevel(path));
    }

    /**
     * HTTP 端点：按游标分页列举对象。
     */
    @GetMapping("/object/list/lazy")
    @Operation(summary = "懒加载文件列表（分页）")
    public OssResponse<?> lazyList(
            @NotBlank @RequestParam String path,
            @RequestParam(defaultValue = "100") @Min(1) int maxKeys,
            @RequestParam(required = false) String continuationToken) {
        return OssResponse.data(ossTemplate.query().lazyList(path, maxKeys, continuationToken));
    }

    /**
     * HTTP 端点：获取完整目录树。
     */
    @GetMapping("/object/tree")
    @Operation(summary = "获取完整目录树")
    public OssResponse<ObjectTreeNode> getObjectTree(@NotBlank @RequestParam String path) {
        return OssResponse.data(ossTemplate.query().getTreeList(path));
    }

    /**
     * HTTP 端点：按关键字搜索目录树。
     */
    @GetMapping("/object/tree/search")
    @Operation(summary = "按关键字搜索并返回目录树")
    public OssResponse<ObjectTreeNode> searchObjectTree(
            @NotBlank @RequestParam String path,
            @NotBlank @RequestParam String keyword) {
        return OssResponse.data(ossTemplate.query().getTreeListByName(path, keyword));
    }

    /**
     * HTTP 端点：获取仅包含目录节点的树结构。
     */
    @GetMapping("/object/tree/folder")
    @Operation(summary = "获取文件夹树（仅文件夹节点）")
    public OssResponse<List<ObjectTreeNode>> getFolderTree(@NotBlank @RequestParam String path) {
        return OssResponse.data(ossTemplate.query().getFolderTreeList(path));
    }

    /**
     * HTTP 端点：列举当前凭证可见的所有 Bucket。
     */
    @GetMapping("/buckets")
    @Operation(summary = "列举所有 Bucket")
    public OssResponse<List<BucketInfo>> listBuckets() {
        return OssResponse.data(ossTemplate.query().getAllBuckets());
    }

    /**
     * HTTP 端点：查询指定 Bucket 的聚合详情。
     */
    @GetMapping("/buckets/{bucketName}")
    @Operation(summary = "获取指定 Bucket 详情")
    public OssResponse<BucketDetailInfo> getBucketDetail(@PathVariable String bucketName) {
        return OssResponse.data(ossTemplate.bucket().getBucketDetail(bucketName));
    }

    /**
     * HTTP 端点：查询指定 Bucket 的 ACL。
     */
    @GetMapping("/buckets/{bucketName}/access")
    @Operation(summary = "获取指定 Bucket ACL")
    public OssResponse<BucketAccessInfo> getBucketAccess(@PathVariable String bucketName) {
        return OssResponse.data(ossTemplate.bucket().getBucketAccess(bucketName));
    }

    /**
     * HTTP 端点：设置指定 Bucket 的 ACL。
     */
    @PutMapping("/buckets/{bucketName}/access")
    @Operation(summary = "设置指定 Bucket ACL")
    public OssResponse<BucketAccessInfo> setBucketAccess(
            @PathVariable String bucketName,
            @NotBlank @RequestParam String acl) {
        return OssResponse.data(ossTemplate.bucket().setBucketAccess(bucketName, acl));
    }

    /**
     * HTTP 端点：按时间回滚指定 Bucket 的当前可见状态。
     */
    @PostMapping("/buckets/{bucketName}/rewind")
    @Operation(summary = "按时间回滚指定 Bucket")
    public OssResponse<BucketRewindResult> rewindBucket(
            @PathVariable String bucketName,
            @NotBlank @RequestParam String targetTime) {
        return OssResponse.data(ossTemplate.bucket().rewindBucket(bucketName, targetTime));
    }

    /**
     * HTTP 端点：测试默认 Bucket 的连通性。
     */
    @GetMapping("/connect")
    @Operation(summary = "测试 OSS 连接")
    public OssResponse<Boolean> testConnect() {
        return OssResponse.data(ossTemplate.query().testConnect());
    }

    // ================================================================
    // 文件预览 / 下载
    // ================================================================

    /**
     * HTTP 端点：以内联方式预览对象内容。
     *
     * <p>该端点直接写入响应体，不走统一 JSON 包装，
     * 因为预览场景需要保持原始文件流和 Range 头语义。</p>
     */
    @GetMapping("/object/preview/**")
    @Operation(summary = "预览文件")
    public void previewObject(HttpServletResponse response, HttpServletRequest request)
            throws IOException {
        ossTemplate.query().previewObject(
                new JakartaOssPreviewContext(request, response),
                extractObjectName(request), false);
    }

    /**
     * HTTP 端点：以附件下载方式输出对象内容。
     */
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

    /**
     * HTTP 端点：复制对象到新位置。
     */
    @PostMapping("/object/copy")
    @Operation(summary = "复制文件")
    public OssResponse<Void> copyObject(
            @NotBlank @RequestParam String sourceKey,
            @NotBlank @RequestParam String destKey) {
        ossTemplate.put().copyFile(sourceKey, destKey);
        return OssResponse.success("复制成功");
    }

    /**
     * HTTP 端点：把对象移动到目标目录。
     */
    @PostMapping("/object/move")
    @Operation(summary = "移动文件")
    public OssResponse<Void> moveObject(
            @NotBlank @RequestParam String sourceKey,
            @NotBlank @RequestParam String destPath) {
        ossTemplate.put().move(sourceKey, destPath);
        return OssResponse.success("移动成功");
    }

    // ================================================================
    // 流式解压
    // ================================================================

    /**
     * HTTP 端点：在默认 Bucket 内流式解压 ZIP 文件。
     */
    @PostMapping("/unzip")
    @Operation(summary = "流式解压 ZIP 文件")
    public OssResponse<UnzipResult> unzip(
            @NotBlank @RequestParam String zipObjectKey,
            @NotBlank @RequestParam String targetPath) {
        return OssResponse.data(ossTemplate.unzip().unzip(zipObjectKey, targetPath));
    }

    /**
     * HTTP 端点：跨 Bucket 流式解压 ZIP 文件。
     */
    @PostMapping("/unzip/cross-bucket")
    @Operation(summary = "跨 Bucket 流式解压")
    public OssResponse<UnzipResult> unzipCrossBucket(
            @NotBlank @RequestParam String sourceBucket,
            @NotBlank @RequestParam String zipObjectKey,
            @NotBlank @RequestParam String targetBucket,
            @NotBlank @RequestParam String targetPath) {
        return OssResponse.data(ossTemplate.unzip().unzip(sourceBucket, zipObjectKey, targetBucket, targetPath));
    }

    /**
     * HTTP 端点：按条目前缀过滤后流式解压 ZIP 文件。
     */
    @PostMapping("/unzip/filter")
    @Operation(summary = "流式解压 ZIP 文件（按路径前缀过滤）")
    public OssResponse<UnzipResult> unzipWithFilter(
            @NotBlank @RequestParam String zipObjectKey,
            @RequestParam(required = false) String entryPrefix,
            @NotBlank @RequestParam String targetPath) {
        return OssResponse.data(ossTemplate.unzip().unzipWithFilter(zipObjectKey, entryPrefix, targetPath));
    }

    // ================================================================
    // 预签名 URL
    // ================================================================

    /**
     * HTTP 端点：生成下载预签名 URL。
     */
    @GetMapping("/presign/get")
    @Operation(summary = "生成文件下载预签名 URL")
    public OssResponse<String> getPresignedUrl(
            @NotBlank @RequestParam String objectName,
            @RequestParam(defaultValue = "3600") @Min(1) long expirationSeconds) {
        return OssResponse.data(ossTemplate.presign()
                .generateGetPresignedUrl(objectName, Duration.ofSeconds(expirationSeconds)));
    }

    /**
     * HTTP 端点：生成上传预签名 URL。
     */
    @GetMapping("/presign/put")
    @Operation(summary = "生成文件上传预签名 URL")
    public OssResponse<String> putPresignedUrl(
            @NotBlank @RequestParam String objectName,
            @RequestParam(defaultValue = "application/octet-stream") String contentType,
            @RequestParam(defaultValue = "3600") @Min(1) long expirationSeconds) {
        return OssResponse.data(ossTemplate.presign().generatePutPresignedUrl(
                objectName, contentType, Duration.ofSeconds(expirationSeconds), null));
    }

    // ================================================================
    // 对象标签
    // ================================================================

    /**
     * HTTP 端点：获取对象标签。
     */
    @GetMapping("/object/tags")
    @Operation(summary = "获取对象标签")
    public OssResponse<Map<String, String>> getObjectTags(@NotBlank @RequestParam String objectName) {
        return OssResponse.data(ossTemplate.tagging().getObjectTags(objectName));
    }

    /**
     * HTTP 端点：覆盖设置对象标签。
     */
    @PutMapping("/object/tags")
    @Operation(summary = "设置对象标签（覆盖）")
    public OssResponse<Void> setObjectTags(
            @NotBlank @RequestParam String objectName,
            @RequestBody @NotNull Map<String, String> tags) {
        ossTemplate.tagging().setObjectTags(objectName, tags);
        return OssResponse.success("标签设置成功");
    }

    /**
     * HTTP 端点：合并更新对象标签。
     */
    @PatchMapping("/object/tags")
    @Operation(summary = "追加/更新对象标签（合并）")
    public OssResponse<Void> mergeObjectTags(
            @NotBlank @RequestParam String objectName,
            @RequestBody @NotNull Map<String, String> tags) {
        ossTemplate.tagging().mergeObjectTags(objectName, tags);
        return OssResponse.success("标签更新成功");
    }

    /**
     * HTTP 端点：删除对象全部标签。
     */
    @DeleteMapping("/object/tags")
    @Operation(summary = "删除对象的所有标签")
    public OssResponse<Void> deleteObjectTags(@NotBlank @RequestParam String objectName) {
        ossTemplate.tagging().deleteObjectTags(objectName);
        return OssResponse.success("标签删除成功");
    }

    // ================================================================
    // Bucket 管理
    // ================================================================

    /**
     * HTTP 端点：创建 Bucket。
     */
    @PostMapping("/bucket")
    @Operation(summary = "创建 Bucket")
    public OssResponse<Void> createBucket(@NotBlank @RequestParam String bucketName) {
        ossTemplate.put().createBucket(bucketName);
        return OssResponse.success("Bucket 创建成功");
    }

    /**
     * HTTP 端点：查询默认 Bucket 的版本控制状态。
     */
    @GetMapping("/bucket/versioning")
    @Operation(summary = "获取 Bucket 版本控制状态")
    public OssResponse<String> getVersioningStatus() {
        return OssResponse.data(ossTemplate.bucket().getVersioningStatus());
    }

    /**
     * HTTP 端点：启用默认 Bucket 的版本控制。
     */
    @PutMapping("/bucket/versioning/enable")
    @Operation(summary = "启用 Bucket 版本控制")
    public OssResponse<Void> enableVersioning() {
        ossTemplate.bucket().enableVersioning();
        return OssResponse.success("版本控制已启用");
    }

    /**
     * HTTP 端点：挂起默认 Bucket 的版本控制。
     */
    @PutMapping("/bucket/versioning/suspend")
    @Operation(summary = "挂起 Bucket 版本控制")
    public OssResponse<Void> suspendVersioning() {
        ossTemplate.bucket().suspendVersioning();
        return OssResponse.success("版本控制已挂起");
    }

    /**
     * HTTP 端点：获取默认 Bucket 的生命周期规则。
     */
    @GetMapping("/bucket/lifecycle")
    @Operation(summary = "获取 Bucket 生命周期规则")
    public OssResponse<List<LifecycleRuleInfo>> getLifecycleRules() {
        return OssResponse.data(ossTemplate.bucket().getLifecycleRules());
    }

    /**
     * HTTP 端点：删除默认 Bucket 的全部生命周期规则。
     */
    @DeleteMapping("/bucket/lifecycle")
    @Operation(summary = "删除 Bucket 所有生命周期规则")
    public OssResponse<Void> deleteLifecycleRules() {
        ossTemplate.bucket().deleteLifecycleRules();
        return OssResponse.success("生命周期规则已删除");
    }

    /**
     * HTTP 端点：为默认 Bucket 添加过期删除规则。
     */
    @PostMapping("/bucket/lifecycle/expiration")
    @Operation(summary = "添加文件过期删除规则")
    public OssResponse<Void> addExpirationRule(
            @NotBlank @RequestParam String ruleId,
            @RequestParam(defaultValue = "") String prefix,
            @RequestParam @Min(1) int expirationDays) {
        ossTemplate.bucket().addExpirationRule(ruleId, prefix, expirationDays);
        return OssResponse.success("过期规则添加成功");
    }

    /**
     * HTTP 端点：获取默认 Bucket 的 CORS 配置。
     */
    @GetMapping("/bucket/cors")
    @Operation(summary = "获取 Bucket CORS 配置")
    public OssResponse<List<CorsRuleInfo>> getCorsRules() {
        return OssResponse.data(ossTemplate.bucket().getCorsRules());
    }

    /**
     * HTTP 端点：为默认 Bucket 设置允许所有来源的 CORS。
     */
    @PutMapping("/bucket/cors/allow-all")
    @Operation(summary = "设置允许所有来源的 CORS")
    public OssResponse<Void> allowAllOriginsCors() {
        ossTemplate.bucket().allowAllOriginsCors();
        return OssResponse.success("CORS 配置成功");
    }

    /**
     * HTTP 端点：删除默认 Bucket 的 CORS 配置。
     */
    @DeleteMapping("/bucket/cors")
    @Operation(summary = "删除 Bucket CORS 配置")
    public OssResponse<Void> deleteCorsRules() {
        ossTemplate.bucket().deleteCorsRules();
        return OssResponse.success("CORS 配置已删除");
    }

    /**
     * HTTP 端点：获取默认 Bucket 的访问策略。
     */
    @GetMapping("/bucket/policy")
    @Operation(summary = "获取 Bucket 访问策略")
    public OssResponse<String> getBucketPolicy() {
        return OssResponse.data(ossTemplate.bucket().getBucketPolicy());
    }

    /**
     * HTTP 端点：设置默认 Bucket 的访问策略。
     */
    @PutMapping("/bucket/policy")
    @Operation(summary = "设置 Bucket 访问策略")
    public OssResponse<Void> putBucketPolicy(@RequestBody @NotBlank String policyJson) {
        ossTemplate.bucket().putBucketPolicy(policyJson);
        return OssResponse.success("策略设置成功");
    }

    /**
     * HTTP 端点：删除默认 Bucket 的访问策略。
     */
    @DeleteMapping("/bucket/policy")
    @Operation(summary = "删除 Bucket 访问策略")
    public OssResponse<Void> deleteBucketPolicy() {
        ossTemplate.bucket().deleteBucketPolicy();
        return OssResponse.success("策略已删除");
    }

    /**
     * HTTP 端点：启用默认 Bucket 的服务端加密。
     */
    @PutMapping("/bucket/encryption/enable")
    @Operation(summary = "启用 Bucket 服务端加密")
    public OssResponse<Void> enableEncryption() {
        ossTemplate.bucket().enableServerSideEncryption();
        return OssResponse.success("加密已启用");
    }

    /**
     * HTTP 端点：开启默认 Bucket 的公共访问屏蔽。
     */
    @PutMapping("/bucket/public-access/block")
    @Operation(summary = "开启 Bucket 公共访问屏蔽")
    public OssResponse<Void> blockAllPublicAccess() {
        ossTemplate.bucket().blockAllPublicAccess();
        return OssResponse.success("公共访问已屏蔽");
    }

    /**
     * HTTP 端点：获取默认 Bucket 标签。
     */
    @GetMapping("/bucket/tags")
    @Operation(summary = "获取 Bucket 标签")
    public OssResponse<Map<String, String>> getBucketTags() {
        return OssResponse.data(ossTemplate.tagging().getBucketTags());
    }

    /**
     * HTTP 端点：覆盖设置默认 Bucket 标签。
     */
    @PutMapping("/bucket/tags")
    @Operation(summary = "设置 Bucket 标签（覆盖）")
    public OssResponse<Void> setBucketTags(@RequestBody @NotNull Map<String, String> tags) {
        ossTemplate.tagging().setBucketTags(tags);
        return OssResponse.success("Bucket 标签设置成功");
    }

    /**
     * HTTP 端点：删除默认 Bucket 的全部标签。
     */
    @DeleteMapping("/bucket/tags")
    @Operation(summary = "删除 Bucket 所有标签")
    public OssResponse<Void> deleteBucketTags() {
        ossTemplate.tagging().deleteBucketTags();
        return OssResponse.success("Bucket 标签已删除");
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
