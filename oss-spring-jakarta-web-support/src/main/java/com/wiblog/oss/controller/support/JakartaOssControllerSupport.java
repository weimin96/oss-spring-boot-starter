package com.wiblog.oss.controller.support;

import com.wiblog.oss.bean.*;
import com.wiblog.oss.bean.chunk.*;
import com.wiblog.oss.controller.OssHttpEndpoint;
import com.wiblog.oss.exception.OssException;
import com.wiblog.oss.resp.OssResponse;
import com.wiblog.oss.service.JakartaOssPreviewContext;
import com.wiblog.oss.service.OssTemplate;
import com.wiblog.oss.util.Util;
import com.wiblog.oss.web.adapter.SpringMultipartUploadFile;
import com.wiblog.oss.web.request.ObjectUploadRequest;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * `jakarta.servlet` 体系共享的 OSS HTTP 端点实现。
 *
 * <p>Boot3 与 Boot4 的控制器逻辑已经没有框架级差异，
 * 因此公共 HTTP 适配、参数校验声明和委派路径统一沉到该基类。
 * 版本 Starter 只负责暴露稳定的公开类型并注册运行时 Bean。</p>
 *
 * @author panwm
 */
@RequiredArgsConstructor
public abstract class JakartaOssControllerSupport implements OssHttpEndpoint {

    private final OssTemplate ossTemplate;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    // ================================================================
    // 分片上传
    // ================================================================

    /**
     * HTTP 端点：初始化分片上传任务。
     */
    @PostMapping("/multipart/init")
    public OssResponse<String> initTask(@Validated ChunkTask chunkTask) {
        return OssResponse.data(ossTemplate.put().initTask(chunkTask));
    }

    /**
     * HTTP 端点：上传单个文件分片。
     */
    @PostMapping("/multipart/chunk")
    public OssResponse<ChunkTarget> chunk(
            @RequestParam Integer chunkNumber,
            @RequestParam String filename,
            @RequestParam String path,
            @RequestParam String guid,
            @RequestParam MultipartFile file,
            @RequestParam String uploadId) {
        Chunk chunk = buildChunkRequest(chunkNumber, filename, path, guid, file, uploadId);
        return OssResponse.data(ossTemplate.put().chunk(chunk.toCommand()));
    }

    /**
     * HTTP 端点：合并已上传的所有分片。
     */
    @PostMapping("/multipart/merge")
    public OssResponse<ObjectInfo> merge(@RequestBody @Validated ChunkMerge chunkMerge) {
        return OssResponse.data(ossTemplate.put().merge(chunkMerge));
    }

    /**
     * HTTP 端点：查询分片上传任务的已上传分片列表。
     */
    @GetMapping("/multipart/parts")
    public OssResponse<List<ChunkPartInfo>> listParts(
            @NotBlank @RequestParam String objectName,
            @NotBlank @RequestParam String uploadId) {
        return OssResponse.data(ossTemplate.put().listParts(
                ossTemplate.getDefaultBucketName(), objectName, uploadId));
    }

    // ================================================================
    // 文件上传
    // ================================================================

    /**
     * HTTP 端点：上传文件到对象存储。
     *
     * <p>控制器只在入口处处理 Spring MultipartFile，
     * 随后转换为共享请求契约，避免 Spring 类型继续向下游扩散。</p>
     */
    @PostMapping("/object")
    public OssResponse<ObjectInfo> uploadObject(
            @RequestParam MultipartFile file,
            @NotBlank @RequestParam String path,
            @RequestParam(required = false) String filename) throws IOException {
        ObjectUploadRequest request = buildObjectUploadRequest(file, path, filename);
        request.validate();
        InputStream in = request.openStream();
        return OssResponse.data(ossTemplate.put().putObject(request.getPath(), request.resolveFilename(), in));
    }

    /**
     * HTTP 端点：创建目录占位对象。
     */
    @PostMapping("/folder")
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
    public OssResponse<Void> deleteObject(@NotBlank @RequestParam String objectName) {
        ossTemplate.delete().removeObject(objectName);
        return OssResponse.success("删除成功");
    }

    /**
     * HTTP 端点：批量删除多个对象或目录。
     */
    @DeleteMapping("/objects")
    public OssResponse<Void> deleteObjects(@RequestBody @NotNull List<String> objectNames) {
        ossTemplate.delete().removeObjects(objectNames);
        return OssResponse.success("批量删除成功");
    }

    /**
     * HTTP 端点：递归删除目录下全部对象。
     */
    @DeleteMapping("/folder")
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
    public OssResponse<ObjectInfo> getObject(@NotBlank @RequestParam String objectName) {
        return OssResponse.data(ossTemplate.query().getObjectInfo(objectName));
    }

    /**
     * HTTP 端点：检查对象是否存在。
     */
    @GetMapping("/object/exists")
    public OssResponse<Boolean> objectExists(@NotBlank @RequestParam String objectName) {
        return OssResponse.data(ossTemplate.query().checkExist(objectName));
    }

    /**
     * HTTP 端点：列举指定前缀下的全部对象。
     */
    @GetMapping("/object/list")
    public OssResponse<List<ObjectInfo>> listObjects(@NotBlank @RequestParam String path) {
        return OssResponse.data(ossTemplate.query().listObjects(path));
    }

    /**
     * HTTP 端点：列举指定前缀的下一层级节点。
     */
    @GetMapping("/object/list/next-level")
    public OssResponse<List<ObjectTreeNode>> listNextLevel(@NotBlank @RequestParam String path) {
        return OssResponse.data(ossTemplate.query().listNextLevel(path));
    }

    /**
     * HTTP 端点：按游标分页列举对象。
     */
    @GetMapping("/object/list/lazy")
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
    public OssResponse<ObjectTreeNode> getObjectTree(@NotBlank @RequestParam String path) {
        return OssResponse.data(ossTemplate.query().getTreeList(path));
    }

    /**
     * HTTP 端点：按关键字搜索目录树。
     */
    @GetMapping("/object/tree/search")
    public OssResponse<ObjectTreeNode> searchObjectTree(
            @NotBlank @RequestParam String path,
            @NotBlank @RequestParam String keyword) {
        return OssResponse.data(ossTemplate.query().getTreeListByName(path, keyword));
    }

    /**
     * HTTP 端点：获取仅包含目录节点的树结构。
     */
    @GetMapping("/object/tree/folder")
    public OssResponse<List<ObjectTreeNode>> getFolderTree(@NotBlank @RequestParam String path) {
        return OssResponse.data(ossTemplate.query().getFolderTreeList(path));
    }

    /**
     * HTTP 端点：列举当前凭证可见的所有 Bucket。
     */
    @GetMapping("/buckets")
    public OssResponse<List<BucketInfo>> listBuckets() {
        return OssResponse.data(ossTemplate.query().getAllBuckets());
    }

    /**
     * HTTP 端点：查询指定 Bucket 的聚合详情。
     */
    @GetMapping("/buckets/{bucketName}")
    public OssResponse<BucketDetailInfo> getBucketDetail(@PathVariable String bucketName) {
        return OssResponse.data(ossTemplate.bucket().getBucketDetail(bucketName));
    }

    /**
     * HTTP 端点：查询指定 Bucket 的 ACL。
     */
    @GetMapping("/buckets/{bucketName}/access")
    public OssResponse<BucketAccessInfo> getBucketAccess(@PathVariable String bucketName) {
        return OssResponse.data(ossTemplate.bucket().getBucketAccess(bucketName));
    }

    /**
     * HTTP 端点：设置指定 Bucket 的 ACL。
     */
    @PutMapping("/buckets/{bucketName}/access")
    public OssResponse<BucketAccessInfo> setBucketAccess(
            @PathVariable String bucketName,
            @NotBlank @RequestParam String acl) {
        return OssResponse.data(ossTemplate.bucket().setBucketAccess(bucketName, acl));
    }

    /**
     * HTTP 端点：按时间回滚指定 Bucket 的当前可见状态。
     */
    @PostMapping("/buckets/{bucketName}/rewind")
    public OssResponse<BucketRewindResult> rewindBucket(
            @PathVariable String bucketName,
            @NotBlank @RequestParam String targetTime) {
        return OssResponse.data(ossTemplate.bucket().rewindBucket(bucketName, targetTime));
    }

    /**
     * HTTP 端点：测试默认 Bucket 的连通性。
     */
    @GetMapping("/connect")
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
    public void downloadObject(HttpServletResponse response, HttpServletRequest request)
            throws IOException {
        ossTemplate.query().previewObject(
                new JakartaOssPreviewContext(request, response),
                extractObjectName(request), true);
    }

    /**
     * HTTP 端点：按前缀列举对象并流式输出 ZIP。
     *
     * <p>S3 不存在真实文件夹，因此这里的 path 表示 prefix。
     * 控制器只负责响应头和失败响应适配，ZIP 构建逻辑全部下沉到 core 读侧服务。</p>
     */
    @GetMapping("/folder/download")
    public void downloadFolderAsZip(
            @NotBlank @RequestParam String path,
            @RequestParam(required = false) String filename,
            HttpServletResponse response) throws IOException {
        String zipFilename = resolveZipFilename(path, filename);
        try {
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", buildAttachmentHeader(zipFilename));
            ossTemplate.query().writeFolderAsZip(path, response.getOutputStream());
        } catch (Exception e) {
            writeFolderZipFailure(response, e);
        }
    }

    // ================================================================
    // 文件复制 / 移动
    // ================================================================

    /**
     * HTTP 端点：复制对象到新位置。
     */
    @PostMapping("/object/copy")
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
    public OssResponse<UnzipResult> unzip(
            @NotBlank @RequestParam String zipObjectKey,
            @NotBlank @RequestParam String targetPath) {
        return OssResponse.data(ossTemplate.unzip().unzip(zipObjectKey, targetPath));
    }

    /**
     * HTTP 端点：跨 Bucket 流式解压 ZIP 文件。
     */
    @PostMapping("/unzip/cross-bucket")
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
    public OssResponse<Map<String, String>> getObjectTags(@NotBlank @RequestParam String objectName) {
        return OssResponse.data(ossTemplate.tagging().getObjectTags(objectName));
    }

    /**
     * HTTP 端点：覆盖设置对象标签。
     */
    @PutMapping("/object/tags")
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
    public OssResponse<Void> createBucket(@NotBlank @RequestParam String bucketName) {
        ossTemplate.put().createBucket(bucketName);
        return OssResponse.success("Bucket 创建成功");
    }

    /**
     * HTTP 端点：查询默认 Bucket 的版本控制状态。
     */
    @GetMapping("/bucket/versioning")
    public OssResponse<String> getVersioningStatus() {
        return OssResponse.data(ossTemplate.bucket().getVersioningStatus());
    }

    /**
     * HTTP 端点：启用默认 Bucket 的版本控制。
     */
    @PutMapping("/bucket/versioning/enable")
    public OssResponse<Void> enableVersioning() {
        ossTemplate.bucket().enableVersioning();
        return OssResponse.success("版本控制已启用");
    }

    /**
     * HTTP 端点：挂起默认 Bucket 的版本控制。
     */
    @PutMapping("/bucket/versioning/suspend")
    public OssResponse<Void> suspendVersioning() {
        ossTemplate.bucket().suspendVersioning();
        return OssResponse.success("版本控制已挂起");
    }

    /**
     * HTTP 端点：获取默认 Bucket 的生命周期规则。
     */
    @GetMapping("/bucket/lifecycle")
    public OssResponse<List<LifecycleRuleInfo>> getLifecycleRules() {
        return OssResponse.data(ossTemplate.bucket().getLifecycleRules());
    }

    /**
     * HTTP 端点：删除默认 Bucket 的全部生命周期规则。
     */
    @DeleteMapping("/bucket/lifecycle")
    public OssResponse<Void> deleteLifecycleRules() {
        ossTemplate.bucket().deleteLifecycleRules();
        return OssResponse.success("生命周期规则已删除");
    }

    /**
     * HTTP 端点：为默认 Bucket 添加过期删除规则。
     */
    @PostMapping("/bucket/lifecycle/expiration")
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
    public OssResponse<List<CorsRuleInfo>> getCorsRules() {
        return OssResponse.data(ossTemplate.bucket().getCorsRules());
    }

    /**
     * HTTP 端点：为默认 Bucket 设置允许所有来源的 CORS。
     */
    @PutMapping("/bucket/cors/allow-all")
    public OssResponse<Void> allowAllOriginsCors() {
        ossTemplate.bucket().allowAllOriginsCors();
        return OssResponse.success("CORS 配置成功");
    }

    /**
     * HTTP 端点：删除默认 Bucket 的 CORS 配置。
     */
    @DeleteMapping("/bucket/cors")
    public OssResponse<Void> deleteCorsRules() {
        ossTemplate.bucket().deleteCorsRules();
        return OssResponse.success("CORS 配置已删除");
    }

    /**
     * HTTP 端点：获取默认 Bucket 的访问策略。
     */
    @GetMapping("/bucket/policy")
    public OssResponse<String> getBucketPolicy() {
        return OssResponse.data(ossTemplate.bucket().getBucketPolicy());
    }

    /**
     * HTTP 端点：设置默认 Bucket 的访问策略。
     */
    @PutMapping("/bucket/policy")
    public OssResponse<Void> putBucketPolicy(@RequestBody @NotBlank String policyJson) {
        ossTemplate.bucket().putBucketPolicy(policyJson);
        return OssResponse.success("策略设置成功");
    }

    /**
     * HTTP 端点：删除默认 Bucket 的访问策略。
     */
    @DeleteMapping("/bucket/policy")
    public OssResponse<Void> deleteBucketPolicy() {
        ossTemplate.bucket().deleteBucketPolicy();
        return OssResponse.success("策略已删除");
    }

    /**
     * HTTP 端点：启用默认 Bucket 的服务端加密。
     */
    @PutMapping("/bucket/encryption/enable")
    public OssResponse<Void> enableEncryption() {
        ossTemplate.bucket().enableServerSideEncryption();
        return OssResponse.success("加密已启用");
    }

    /**
     * HTTP 端点：开启默认 Bucket 的公共访问屏蔽。
     */
    @PutMapping("/bucket/public-access/block")
    public OssResponse<Void> blockAllPublicAccess() {
        ossTemplate.bucket().blockAllPublicAccess();
        return OssResponse.success("公共访问已屏蔽");
    }

    /**
     * HTTP 端点：获取默认 Bucket 标签。
     */
    @GetMapping("/bucket/tags")
    public OssResponse<Map<String, String>> getBucketTags() {
        return OssResponse.data(ossTemplate.tagging().getBucketTags());
    }

    /**
     * HTTP 端点：覆盖设置默认 Bucket 标签。
     */
    @PutMapping("/bucket/tags")
    public OssResponse<Void> setBucketTags(@RequestBody @NotNull Map<String, String> tags) {
        ossTemplate.tagging().setBucketTags(tags);
        return OssResponse.success("Bucket 标签设置成功");
    }

    /**
     * HTTP 端点：删除默认 Bucket 的全部标签。
     */
    @DeleteMapping("/bucket/tags")
    public OssResponse<Void> deleteBucketTags() {
        ossTemplate.tagging().deleteBucketTags();
        return OssResponse.success("Bucket 标签已删除");
    }

    // ================================================================
    // 私有工具
    // ================================================================

    private Chunk buildChunkRequest(Integer chunkNumber, String filename, String path,
                                    String guid, MultipartFile file, String uploadId) {
        Chunk chunk = new Chunk();
        chunk.setChunkNumber(chunkNumber);
        chunk.setFilename(filename);
        chunk.setPath(path);
        chunk.setGuid(guid);
        chunk.setFile(file == null ? null : new SpringMultipartUploadFile(file));
        chunk.setUploadId(uploadId);
        return chunk;
    }

    private ObjectUploadRequest buildObjectUploadRequest(MultipartFile file, String path, String filename) {
        ObjectUploadRequest request = new ObjectUploadRequest();
        request.setFile(file == null ? null : new SpringMultipartUploadFile(file));
        request.setPath(path);
        request.setFilename(filename);
        return request;
    }

    private String extractObjectName(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return antPathMatcher.extractPathWithinPattern(pattern, path);
    }

    private String resolveZipFilename(String path, String filename) {
        String normalizedName = Util.isBlank(filename)
                ? Util.getFilename(trimTrailingSlash(path))
                : filename.trim();
        if (Util.isBlank(normalizedName)) {
            normalizedName = "folder-download";
        }
        return normalizedName.toLowerCase(Locale.ROOT).endsWith(".zip")
                ? normalizedName
                : normalizedName + ".zip";
    }

    private String buildAttachmentHeader(String filename) throws IOException {
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.name())
                .replace("+", "%20");
        return "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename;
    }

    /**
     * 该端点输出的是二进制 ZIP，不适合再交给统一 JSON 异常处理器。
     * 如果压缩流程在响应提交前失败，这里直接回写明确的 HTTP 状态和纯文本消息。
     */
    private void writeFolderZipFailure(HttpServletResponse response, Exception exception) throws IOException {
        if (response.isCommitted()) {
            if (exception instanceof IOException) {
                throw (IOException) exception;
            }
            if (exception instanceof RuntimeException) {
                throw (RuntimeException) exception;
            }
            throw new IOException(exception);
        }
        response.reset();
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/plain;charset=UTF-8");
        response.setStatus(resolveFailureStatus(exception));
        response.getOutputStream().write(resolveFailureMessage(exception).getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }

    private int resolveFailureStatus(Exception exception) {
        if (!(exception instanceof OssException)) {
            return 500;
        }
        OssException ossException = (OssException) exception;
        if ("INVALID_PATH".equals(ossException.getCode())) {
            return 400;
        }
        if ("OBJECT_NOT_FOUND".equals(ossException.getCode())) {
            return 404;
        }
        return 500;
    }

    private String resolveFailureMessage(Exception exception) {
        return exception instanceof OssException
                ? exception.getMessage()
                : "文件夹压缩下载失败";
    }

    private String trimTrailingSlash(String path) {
        if (path == null) {
            return null;
        }
        String normalizedPath = path.replace('\\', '/');
        return normalizedPath.endsWith("/") ? normalizedPath.substring(0, normalizedPath.length() - 1) : normalizedPath;
    }
}
