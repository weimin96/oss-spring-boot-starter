package com.wiblog.oss.sample;

import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.resp.OssResponse;
import com.wiblog.oss.service.OssTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * 自定义业务控制器示例，展示如何直接注入并使用 {@link OssTemplate}。
 *
 * @author pwm
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class SampleFileController {

    private final OssTemplate ossTemplate;

    @PostMapping("/avatar")
    public OssResponse<String> uploadAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        ObjectInfo info = ossTemplate.put().putObject(
                "avatars/", file.getOriginalFilename(), file.getInputStream());
        return OssResponse.data(info.getUrl());
    }

    @GetMapping("/list")
    public OssResponse<List<ObjectInfo>> listFiles(@RequestParam(defaultValue = "avatars/") String path) {
        return OssResponse.data(ossTemplate.query().listObjects(path));
    }

    @GetMapping("/presign")
    public OssResponse<String> presign(
            @RequestParam String objectName,
            @RequestParam(defaultValue = "3600") long seconds) {
        String url = ossTemplate.presign()
                .generateGetPresignedUrl(objectName, Duration.ofSeconds(seconds));
        return OssResponse.data(url);
    }

    @DeleteMapping
    public OssResponse<Void> delete(@RequestParam String objectName) {
        ossTemplate.delete().removeObject(objectName);
        return OssResponse.success("删除成功");
    }
}
