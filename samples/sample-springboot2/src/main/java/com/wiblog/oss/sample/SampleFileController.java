package com.wiblog.oss.sample;

import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.resp.OssResponse;
import com.wiblog.oss.service.OssTemplate;
import com.wiblog.oss.util.Util;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * 自定义业务控制器示例，展示如何直接注入并使用 {@link OssTemplate}。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class SampleFileController {

    /**
     * 由 OssAutoConfiguration2 自动注册，直接注入即可
     */
    private final OssTemplate ossTemplate;

    /**
     * 上传头像示例：将文件存入 avatars/ 目录，返回完整访问 URL
     */
    @PostMapping("/avatar")
    public OssResponse<String> uploadAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        ObjectInfo info = ossTemplate.put().putObject(
                "avatars/", file.getOriginalFilename(), file.getInputStream());
        return OssResponse.data(info.getUrl());
    }

    /**
     * 列举某目录下所有文件
     */
    @GetMapping("/list")
    public OssResponse<List<ObjectInfo>> listFiles(@RequestParam(defaultValue = "avatars/") String path) {
        return OssResponse.data(ossTemplate.query().listObjects(path));
    }

    /**
     * 检查文件是否存在
     */
    @GetMapping("/exists")
    public OssResponse<Boolean> exists(@RequestParam String objectName) {
        return OssResponse.data(ossTemplate.query().checkExist(objectName));
    }

    /**
     * 自定义业务下载示例：把指定 prefix 下的对象流式压缩为 ZIP。
     *
     * <p>这里故意不依赖内置 `/oss` 控制器，目的是展示业务控制器如何直接复用 starter 暴露的 Java API。</p>
     */
    @GetMapping("/folder/download")
    public void downloadFolderAsZip(
            @RequestParam String path,
            @RequestParam(required = false) String filename,
            HttpServletResponse response) throws IOException {
        String zipFilename = resolveZipFilename(path, filename);
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", buildAttachmentHeader(zipFilename));
        ossTemplate.query().writeFolderAsZip(path, response.getOutputStream());
    }

    /**
     * 删除文件
     */
    @DeleteMapping
    public OssResponse<Void> delete(@RequestParam String objectName) {
        ossTemplate.delete().removeObject(objectName);
        return OssResponse.success("删除成功");
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

    private String trimTrailingSlash(String path) {
        if (path == null) {
            return null;
        }
        String normalizedPath = path.replace('\\', '/');
        return normalizedPath.endsWith("/") ? normalizedPath.substring(0, normalizedPath.length() - 1) : normalizedPath;
    }
}
