package com.wiblog.oss.controller;

import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;
import com.wiblog.oss.bean.UnzipResult;
import com.wiblog.oss.bean.chunk.Chunk;
import com.wiblog.oss.bean.chunk.ChunkMerge;
import com.wiblog.oss.bean.chunk.ChunkTarget;
import com.wiblog.oss.bean.chunk.ChunkTask;
import com.wiblog.oss.resp.R;
import com.wiblog.oss.service.OssTemplate;
import com.wiblog.oss.util.Util;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * OSS HTTP 端点。
 *
 * <p>提供完整的对象存储 REST 接口，包括：</p>
 * <ul>
 *   <li>文件上传（普通上传 / 分片上传）</li>
 *   <li>文件查询（列表、详情、树形结构、懒加载）</li>
 *   <li>文件预览与下载（支持 Range 分段）</li>
 *   <li>文件删除（单个 / 批量 / 文件夹）</li>
 *   <li>文件复制与移动</li>
 *   <li>流式解压（ZIP 直接解压到 OSS）</li>
 *   <li>预签名 URL（GET / PUT）</li>
 *   <li>对象标签管理</li>
 *   <li>Bucket 管理（版本控制、生命周期、CORS、加密）</li>
 * </ul>
 *
 * @author panwm
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("${oss.http.prefix:}/oss")
@Tag(name = "OSS 对象存储接口")
public class OssController {

    private final OssTemplate ossTemplate;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    // ================================================================
    // 分片上传
    // ================================================================

    @PostMapping("/multipart/init")
    @Operation(summary = "初始化分片上传任务", description = "返回 uploadId，后续分片上传和合并都需要此 ID")
    public R<String> initTask(@Validated ChunkTask chunkTask) {
        String uploadId = ossTemplate.put().initTask(chunkTask);
        return R.data(uploadId);
    }

    @PostMapping("/multipart/chunk")
    @Operation(summary = "上传文件分片", description = "按分片号上传，允许并发，顺序无关")
    public R<ChunkTarget> chunk(@Validated Chunk chunk) {
        ChunkTarget target = ossTemplate.put().chunk(chunk);
        return R.data(target);
    }

    @PostMapping("/multipart/merge")
    @Operation(summary = "合并分片", description = "所有分片上传完毕后调用，完成对象创建")
    public R<ObjectInfo> merge(@Validated ChunkMerge chunkMerge) {
        ObjectInfo info = ossTemplate.put().merge(chunkMerge);
        return R.data(info);
    }

    @GetMapping("/multipart/parts")
    @Operation(summary = "查询已上传的分片列表", description = "可用于断点续传场景，判断哪些分片已上传")
    public R<List<?>> listParts(
            @Parameter(description = "对象 key", required = true) @NotBlank @RequestParam String objectName,
            @Parameter(description = "uploadId", required = true) @NotBlank @RequestParam String uploadId) {
        List<?> parts = ossTemplate.put().listParts(
                ossTemplate.query().getOssProperties().getBucketName(), objectName, uploadId);
        return R.data(parts);
    }

    // ================================================================
    // 文件上传
    // ================================================================

    @PostMapping("/object")
    @Operation(summary = "上传文件", description = "单文件上传，支持自定义存储路径和文件名")
    public R<ObjectInfo> uploadObject(
            @Parameter(description = "上传文件", required = true)
            @NotNull @RequestParam("file") MultipartFile file,
            @Parameter(description = "存放路径", required = true)
            @NotBlank @RequestParam String path,
            @Parameter(description = "文件名，为空时使用原始文件名")
            @RequestParam(required = false) String filename) throws IOException {
        InputStream in = file.getInputStream();
        String name = Util.isBlank(filename) ? file.getOriginalFilename() : filename;
        ObjectInfo info = ossTemplate.put().putObject(path, name, in);
        return R.data(info);
    }

    @PostMapping("/folder")
    @Operation(summary = "创建文件夹（目录占位符）")
    public R<ObjectInfo> createFolder(
            @Parameter(description = "文件夹路径", required = true) @NotBlank @RequestParam String path) {
        ObjectInfo info = ossTemplate.put().mkdirs(path);
        return R.data(info);
    }

    // ================================================================
    // 文件删除
    // ================================================================

    @DeleteMapping("/object")
    @Operation(summary = "删除单个文件")
    public R<Void> deleteObject(
            @Parameter(description = "文件全路径（key）", required = true)
            @NotBlank @RequestParam String objectName) {
        ossTemplate.delete().removeObject(objectName);
        return R.success("删除成功");
    }

    @DeleteMapping("/objects")
    @Operation(summary = "批量删除文件", description = "一次最多 1000 个，超出请分批调用")
    public R<Void> deleteObjects(
            @Parameter(description = "文件 key 列表", required = true)
            @RequestBody @NotNull List<String> objectNames) {
        objectNames.forEach(key -> ossTemplate.delete().removeObject(key));
        return R.success("批量删除成功");
    }

    @DeleteMapping("/folder")
    @Operation(summary = "删除文件夹（递归删除文件夹下所有对象）")
    public R<Void> deleteFolder(
            @Parameter(description = "文件夹路径", required = true)
            @NotBlank @RequestParam String path) {
        ossTemplate.delete().removeFolder(path);
        return R.success("删除成功");
    }

    // ================================================================
    // 文件查询
    // ================================================================

    @GetMapping("/object")
    @Operation(summary = "获取文件详情（元数据）")
    public R<ObjectInfo> getObject(
            @Parameter(description = "文件全路径", required = true)
            @NotBlank @RequestParam String objectName) {
        ObjectInfo info = ossTemplate.query().getObjectInfo(objectName);
        return R.data(info);
    }

    @GetMapping("/object/exists")
    @Operation(summary = "检查文件是否存在")
    public R<Boolean> objectExists(
            @Parameter(description = "文件全路径", required = true)
            @NotBlank @RequestParam String objectName) {
        boolean exists = ossTemplate.query().checkExist(objectName);
        return R.data(exists);
    }

    @GetMapping("/object/list")
    @Operation(summary = "列举指定路径下的所有对象（含子路径）")
    public R<List<ObjectInfo>> listObjects(
            @Parameter(description = "目录路径", required = true)
            @NotBlank @RequestParam String path) {
        List<ObjectInfo> list = ossTemplate.query().listObjects(path);
        return R.data(list);
    }

    @GetMapping("/object/list/next-level")
    @Operation(summary = "列举指定路径下一层级的文件和文件夹")
    public R<List<ObjectTreeNode>> listNextLevel(
            @Parameter(description = "目录路径", required = true)
            @NotBlank @RequestParam String path) {
        List<ObjectTreeNode> list = ossTemplate.query().listNextLevel(path);
        return R.data(list);
    }

    @GetMapping("/object/list/lazy")
    @Operation(summary = "懒加载文件列表（分页）",
            description = "首次调用不传 continuationToken，后续将上次返回的 token 传入实现翻页")
    public R<?> lazyList(
            @Parameter(description = "目录路径", required = true) @NotBlank @RequestParam String path,
            @Parameter(description = "每页数量，默认 100") @RequestParam(defaultValue = "100") @Min(1) int maxKeys,
            @Parameter(description = "分页 token，首次无需传入") @RequestParam(required = false) String continuationToken) {
        return R.data(ossTemplate.query().lazyList(path, maxKeys, continuationToken));
    }

    @GetMapping("/object/tree")
    @Operation(summary = "获取完整目录树")
    public R<ObjectTreeNode> getObjectTree(
            @Parameter(description = "根路径", required = true)
            @NotBlank @RequestParam String path) {
        ObjectTreeNode tree = ossTemplate.query().getTreeList(path);
        return R.data(tree);
    }

    @GetMapping("/object/tree/search")
    @Operation(summary = "按关键字搜索并返回目录树")
    public R<ObjectTreeNode> searchObjectTree(
            @Parameter(description = "根路径", required = true) @NotBlank @RequestParam String path,
            @Parameter(description = "搜索关键字", required = true) @NotBlank @RequestParam String keyword) {
        ObjectTreeNode tree = ossTemplate.query().getTreeListByName(path, keyword);
        return R.data(tree);
    }

    @GetMapping("/object/tree/folder")
    @Operation(summary = "获取文件夹树（仅包含文件夹节点，不含文件）")
    public R<List<ObjectTreeNode>> getFolderTree(
            @Parameter(description = "根路径", required = true)
            @NotBlank @RequestParam String path) {
        List<ObjectTreeNode> tree = ossTemplate.query().getFolderTreeList(path);
        return R.data(tree);
    }

    @GetMapping("/buckets")
    @Operation(summary = "列举所有 Bucket")
    public R<List<?>> listBuckets() {
        return R.data(ossTemplate.query().getAllBuckets());
    }

    @GetMapping("/connect")
    @Operation(summary = "测试 OSS 连接与 Bucket 可访问性")
    public R<Boolean> testConnect() {
        boolean ok = ossTemplate.query().testConnect();
        return R.data(ok);
    }

    // ================================================================
    // 文件预览 / 下载
    // ================================================================

    @GetMapping("/object/preview/**")
    @Operation(summary = "预览文件", description = "以 inline 方式呈现，支持 Range 分段请求，适用于视频/音频流")
    public void previewObject(HttpServletResponse response, HttpServletRequest request) throws IOException {
        ossTemplate.query().previewObject(request, response, extractObjectName(request), false);
    }

    @GetMapping("/object/download/**")
    @Operation(summary = "下载文件", description = "以 attachment 方式触发浏览器下载")
    public void downloadObject(HttpServletResponse response, HttpServletRequest request) throws IOException {
        ossTemplate.query().previewObject(request, response, extractObjectName(request), true);
    }

    // ================================================================
    // 文件复制 / 移动
    // ================================================================

    @PostMapping("/object/copy")
    @Operation(summary = "复制文件（同 Bucket 内）")
    public R<Void> copyObject(
            @Parameter(description = "源文件 key", required = true) @NotBlank @RequestParam String sourceKey,
            @Parameter(description = "目标文件 key", required = true) @NotBlank @RequestParam String destKey) {
        ossTemplate.put().copyFile(sourceKey, destKey);
        return R.success("复制成功");
    }

    @PostMapping("/object/move")
    @Operation(summary = "移动文件（复制后删除源文件）")
    public R<Void> moveObject(
            @Parameter(description = "源文件 key", required = true) @NotBlank @RequestParam String sourceKey,
            @Parameter(description = "目标目录路径", required = true) @NotBlank @RequestParam String destPath) {
        ossTemplate.put().move(sourceKey, destPath);
        return R.success("移动成功");
    }

    // ================================================================
    // 流式解压
    // ================================================================

    @PostMapping("/unzip")
    @Operation(summary = "流式解压 ZIP 文件",
            description = "将 OSS 中的 ZIP 对象边下载边解压，解压后的文件写入目标路径，无需落盘")
    public R<UnzipResult> unzip(
            @Parameter(description = "ZIP 文件的 key", required = true)
            @NotBlank @RequestParam String zipObjectKey,
            @Parameter(description = "解压后文件存放的目标路径前缀", required = true)
            @NotBlank @RequestParam String targetPath) {
        UnzipResult result = ossTemplate.unzip().unzip(zipObjectKey, targetPath);
        return R.data(result);
    }

    @PostMapping("/unzip/cross-bucket")
    @Operation(summary = "跨 Bucket 流式解压 ZIP 文件")
    public R<UnzipResult> unzipCrossBucket(
            @Parameter(description = "源 Bucket", required = true) @NotBlank @RequestParam String sourceBucket,
            @Parameter(description = "ZIP 文件 key", required = true) @NotBlank @RequestParam String zipObjectKey,
            @Parameter(description = "目标 Bucket", required = true) @NotBlank @RequestParam String targetBucket,
            @Parameter(description = "目标路径前缀", required = true) @NotBlank @RequestParam String targetPath) {
        UnzipResult result = ossTemplate.unzip().unzip(sourceBucket, zipObjectKey, targetBucket, targetPath);
        return R.data(result);
    }

    @PostMapping("/unzip/filter")
    @Operation(summary = "流式解压 ZIP 文件（按路径前缀过滤）",
            description = "只解压 ZIP 包内以 entryPrefix 开头的条目，实现「只解压部分文件」的需求")
    public R<UnzipResult> unzipWithFilter(
            @Parameter(description = "ZIP 文件 key", required = true) @NotBlank @RequestParam String zipObjectKey,
            @Parameter(description = "ZIP 内条目路径前缀，为空则解压全部") @RequestParam(required = false) String entryPrefix,
            @Parameter(description = "目标路径前缀", required = true) @NotBlank @RequestParam String targetPath) {
        UnzipResult result = ossTemplate.unzip().unzipWithFilter(zipObjectKey, entryPrefix, targetPath);
        return R.data(result);
    }

    // ================================================================
    // 预签名 URL
    // ================================================================

    @GetMapping("/presign/get")
    @Operation(summary = "生成文件下载预签名 URL",
            description = "生成带临时鉴权的下载链接，默认有效期 1 小时，最长 7 天")
    public R<String> getPresignedUrl(
            @Parameter(description = "对象 key", required = true) @NotBlank @RequestParam String objectName,
            @Parameter(description = "有效时长（秒），默认 3600") @RequestParam(defaultValue = "3600") @Min(1) long expirationSeconds) {
        String url = ossTemplate.presign().generateGetPresignedUrl(objectName, Duration.ofSeconds(expirationSeconds));
        return R.data(url);
    }

    @GetMapping("/presign/put")
    @Operation(summary = "生成文件上传预签名 URL",
            description = "前端可直接用此 URL 通过 HTTP PUT 上传文件，无需后端转发，减少带宽占用")
    public R<String> putPresignedUrl(
            @Parameter(description = "目标对象 key", required = true) @NotBlank @RequestParam String objectName,
            @Parameter(description = "MIME 类型，如 image/jpeg") @RequestParam(defaultValue = "application/octet-stream") String contentType,
            @Parameter(description = "有效时长（秒），默认 3600") @RequestParam(defaultValue = "3600") @Min(1) long expirationSeconds) {
        String url = ossTemplate.presign().generatePutPresignedUrl(
                objectName, contentType, Duration.ofSeconds(expirationSeconds), null);
        return R.data(url);
    }

    // ================================================================
    // 对象标签
    // ================================================================

    @GetMapping("/object/tags")
    @Operation(summary = "获取对象标签")
    public R<Map<String, String>> getObjectTags(
            @Parameter(description = "对象 key", required = true) @NotBlank @RequestParam String objectName) {
        Map<String, String> tags = ossTemplate.tagging().getObjectTags(objectName);
        return R.data(tags);
    }

    @PutMapping("/object/tags")
    @Operation(summary = "设置对象标签（覆盖）", description = "会替换对象上已有的所有标签，每个对象最多 10 个标签")
    public R<Void> setObjectTags(
            @Parameter(description = "对象 key", required = true) @NotBlank @RequestParam String objectName,
            @RequestBody @NotNull Map<String, String> tags) {
        ossTemplate.tagging().setObjectTags(objectName, tags);
        return R.success("标签设置成功");
    }

    @PatchMapping("/object/tags")
    @Operation(summary = "追加/更新对象标签（合并）", description = "保留已有标签，仅更新/新增指定键")
    public R<Void> mergeObjectTags(
            @Parameter(description = "对象 key", required = true) @NotBlank @RequestParam String objectName,
            @RequestBody @NotNull Map<String, String> tags) {
        ossTemplate.tagging().mergeObjectTags(objectName, tags);
        return R.success("标签更新成功");
    }

    @DeleteMapping("/object/tags")
    @Operation(summary = "删除对象的所有标签")
    public R<Void> deleteObjectTags(
            @Parameter(description = "对象 key", required = true) @NotBlank @RequestParam String objectName) {
        ossTemplate.tagging().deleteObjectTags(objectName);
        return R.success("标签删除成功");
    }

    // ================================================================
    // Bucket 管理
    // ================================================================

    @PostMapping("/bucket")
    @Operation(summary = "创建 Bucket")
    public R<Void> createBucket(
            @Parameter(description = "Bucket 名称", required = true) @NotBlank @RequestParam String bucketName) {
        ossTemplate.put().createBucket(bucketName);
        return R.success("Bucket 创建成功");
    }

    @GetMapping("/bucket/versioning")
    @Operation(summary = "获取 Bucket 版本控制状态")
    public R<String> getVersioningStatus() {
        String status = ossTemplate.bucket().getVersioningStatus();
        return R.data(status);
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
    @Operation(summary = "添加文件过期删除规则",
            description = "指定路径前缀下的文件超过 expirationDays 天后自动删除")
    public R<Void> addExpirationRule(
            @Parameter(description = "规则 ID", required = true) @NotBlank @RequestParam String ruleId,
            @Parameter(description = "作用路径前缀，为空表示全 Bucket") @RequestParam(defaultValue = "") String prefix,
            @Parameter(description = "过期天数", required = true) @RequestParam @Min(1) int expirationDays) {
        ossTemplate.bucket().addExpirationRule(ruleId, prefix, expirationDays);
        return R.success("过期规则添加成功");
    }

    @GetMapping("/bucket/cors")
    @Operation(summary = "获取 Bucket CORS 配置")
    public R<List<?>> getCorsRules() {
        return R.data(ossTemplate.bucket().getCorsRules());
    }

    @PutMapping("/bucket/cors/allow-all")
    @Operation(summary = "设置 Bucket 允许所有来源的 CORS（前端直传场景）")
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
        String policy = ossTemplate.bucket().getBucketPolicy();
        return R.data(policy);
    }

    @PutMapping("/bucket/policy")
    @Operation(summary = "设置 Bucket 访问策略", description = "传入 JSON 格式的 IAM 策略文档")
    public R<Void> putBucketPolicy(
            @RequestBody @NotBlank String policyJson) {
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
    @Operation(summary = "启用 Bucket 服务端加密（SSE-S3 / AES-256）")
    public R<Void> enableEncryption() {
        ossTemplate.bucket().enableServerSideEncryption();
        return R.success("加密已启用");
    }

    @PutMapping("/bucket/public-access/block")
    @Operation(summary = "开启 Bucket 公共访问屏蔽（最高安全级别）")
    public R<Void> blockAllPublicAccess() {
        ossTemplate.bucket().blockAllPublicAccess();
        return R.success("公共访问已屏蔽");
    }

    // ================================================================
    // Bucket 标签
    // ================================================================

    @GetMapping("/bucket/tags")
    @Operation(summary = "获取 Bucket 标签")
    public R<Map<String, String>> getBucketTags() {
        Map<String, String> tags = ossTemplate.tagging().getBucketTags();
        return R.data(tags);
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
    // 兼容旧版端点（保留，避免升级时接口断裂）
    // ================================================================

    /**
     * @deprecated 请使用 POST /multipart/init
     */
    @Deprecated
    @PostMapping("/initTask")
    @Operation(summary = "[已废弃] 初始化分片上传，请改用 /multipart/init")
    public R<String> initTaskLegacy(@Validated ChunkTask chunkTask) {
        return initTask(chunkTask);
    }

    /**
     * @deprecated 请使用 POST /multipart/chunk
     */
    @Deprecated
    @PostMapping("/chunk")
    @Operation(summary = "[已废弃] 上传分片，请改用 /multipart/chunk")
    public R<ChunkTarget> chunkLegacy(@Validated Chunk chunk) {
        return chunk(chunk);
    }

    /**
     * @deprecated 请使用 POST /multipart/merge
     */
    @Deprecated
    @PostMapping("/merge")
    @Operation(summary = "[已废弃] 合并分片，请改用 /multipart/merge")
    public R<ObjectInfo> mergeLegacy(@Validated ChunkMerge chunkMerge) {
        return merge(chunkMerge);
    }

    /**
     * @deprecated 请使用 GET /object
     */
    @Deprecated
    @GetMapping("/object/getObject")
    @Operation(summary = "[已废弃] 获取文件信息，请改用 GET /object")
    public R<ObjectInfo> getObjectLegacy(@NotBlank @RequestParam String objectName) {
        return getObject(objectName);
    }

    // ================================================================
    // 私有工具
    // ================================================================

    /**
     * 从通配符映射路径中提取对象 key。
     * 例：请求路径 /oss/object/preview/images/foo.jpg → 返回 images/foo.jpg
     */
    private String extractObjectName(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return antPathMatcher.extractPathWithinPattern(pattern, path);
    }
}