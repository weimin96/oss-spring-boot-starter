package com.wiblog.oss.controller;

import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.chunk.ChunkTarget;
import com.wiblog.oss.openapi.CommonOssOpenApiOperations;
import com.wiblog.oss.resp.OssResponse;
import com.wiblog.oss.service.OssTemplate;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Spring Boot 4.x 带文档能力的 OSS HTTP 端点。
 *
 * <p>运行时逻辑直接复用 {@link OssController4}，
 * 这里只通过实现 {@link CommonOssOpenApiOperations} 与覆盖文件流端点打开 Swagger 元数据。</p>
 *
 * @author panwm
 */
@RestController
public class OpenApiOssController4 extends OssController4 implements CommonOssOpenApiOperations {

    public OpenApiOssController4(OssTemplate ossTemplate) {
        super(ossTemplate);
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
    @Operation(summary = "上传文件")
    @PostMapping("/object")
    public OssResponse<ObjectInfo> uploadObject(
            @RequestParam MultipartFile file,
            @NotBlank @RequestParam String path,
            @RequestParam(required = false) String filename) throws IOException {
        return super.uploadObject(file, path, filename);
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
}
