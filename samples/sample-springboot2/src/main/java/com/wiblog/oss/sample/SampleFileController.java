package com.wiblog.oss.sample;

import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.resp.OssResponse;
import com.wiblog.oss.service.OssTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 自定义业务控制器示例，展示如何直接注入并使用 {@link OssTemplate}。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class SampleFileController {

    /** 由 OssAutoConfiguration2 自动注册，直接注入即可 */
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
     * 删除文件
     */
    @DeleteMapping
    public OssResponse<Void> delete(@RequestParam String objectName) {
        ossTemplate.delete().removeObject(objectName);
        return OssResponse.success("删除成功");
    }
}
