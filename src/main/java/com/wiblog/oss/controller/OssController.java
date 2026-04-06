package com.wiblog.oss.controller;

import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.util.AntPathMatcher;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * OSS HTTP 端点。
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

    // ----------------------------------------------------------------
    // 分片上传
    // ----------------------------------------------------------------

    @PostMapping("/initTask")
    @Operation(summary = "初始化分片上传任务")
    public R<String> initTask(@Validated ChunkTask chunkTask) {
        String uploadId = ossTemplate.put().initTask(chunkTask);
        return R.data(uploadId);
    }

    @PostMapping("/chunk")
    @Operation(summary = "上传文件分片")
    public R<ChunkTarget> chunk(@Validated Chunk chunk) {
        ChunkTarget target = ossTemplate.put().chunk(chunk);
        return R.data(target);
    }

    /**
     * 去掉 multipart 限制，是因为该接口只接收表单字段，不直接接收文件内容。
     */
    @PostMapping("/merge")
    @Operation(summary = "合并分片")
    public R<ObjectInfo> merge(@Validated ChunkMerge chunkMerge) {
        ObjectInfo info = ossTemplate.put().merge(chunkMerge);
        return R.data(info);
    }

    // ----------------------------------------------------------------
    // 文件上传
    // ----------------------------------------------------------------

    @PostMapping("/object")
    @Operation(summary = "上传文件")
    public R<ObjectInfo> uploadObject(
            @Parameter(description = "上传文件", required = true)
            @NotNull @RequestParam("file") MultipartFile file,
            @Parameter(description = "存放路径", required = true)
            @NotBlank String path,
            @Parameter(description = "文件名，为空时使用原始文件名")
            String filename) throws IOException {
        InputStream in = file.getInputStream();
        // 保持原有兼容行为：未显式传文件名时，回退到浏览器提交的原始文件名。
        String name = Util.isBlank(filename) ? file.getOriginalFilename() : filename;
        ObjectInfo info = ossTemplate.put().putObject(path, name, in);
        return R.data(info);
    }

    // ----------------------------------------------------------------
    // 文件删除
    // ----------------------------------------------------------------

    @DeleteMapping("/object")
    @Operation(summary = "删除文件")
    public R<Void> deleteObject(@Parameter(description = "文件全路径", required = true) @NotBlank String objectName) {
        ossTemplate.delete().removeObject(objectName);
        return R.success("删除成功");
    }

    @DeleteMapping("/folder")
    @Operation(summary = "删除文件夹")
    public R<Void> deleteFolder(@Parameter(description = "文件夹路径", required = true) @NotBlank String path) {
        ossTemplate.delete().removeFolder(path);
        return R.success("删除成功");
    }

    // ----------------------------------------------------------------
    // 文件查询
    // ----------------------------------------------------------------

    @GetMapping("/object/getObject")
    @Operation(summary = "获取文件信息")
    public R<ObjectInfo> getObject(@Parameter(description = "文件全路径", required = true) @NotBlank String objectName) {
        ObjectInfo info = ossTemplate.query().getObjectInfo(objectName);
        return R.data(info);
    }

    @GetMapping("/object/list")
    @Operation(summary = "获取文件列表")
    public R<List<ObjectInfo>> getObjectList(@Parameter(description = "目录路径", required = true) @NotBlank String objectName) {
        List<ObjectInfo> list = ossTemplate.query().listObjects(objectName);
        return R.data(list);
    }

    @GetMapping("/object/tree")
    @Operation(summary = "获取目录树")
    public R<ObjectTreeNode> getObjectTree(@Parameter(description = "目录路径", required = true) @NotBlank String objectName) {
        ObjectTreeNode tree = ossTemplate.query().getTreeList(objectName);
        return R.data(tree);
    }

    // ----------------------------------------------------------------
    // 文件预览 / 下载
    // ----------------------------------------------------------------

    @GetMapping("/object/preview/**")
    @Operation(summary = "预览文件", description = "以内联方式预览对象存储中的文件")
    public void previewObject(HttpServletResponse response, HttpServletRequest request) throws IOException {
        ossTemplate.query().previewObject(request, response, extractObjectName(request), false);
    }

    @GetMapping("/object/download/**")
    @Operation(summary = "下载文件", description = "以附件方式触发浏览器下载文件")
    public void downloadObject(HttpServletResponse response, HttpServletRequest request) throws IOException {
        ossTemplate.query().previewObject(request, response, extractObjectName(request), true);
    }

    // ----------------------------------------------------------------
    // 私有工具
    // ----------------------------------------------------------------

    /**
     * 提取通配符路径，是为了避免控制器重复拼接预览与下载端点下的对象名解析逻辑。
     */
    private String extractObjectName(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return antPathMatcher.extractPathWithinPattern(pattern, path);
    }
}
