package com.wiblog.oss.sample;

import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.resp.R;
import com.wiblog.oss.service.OssTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * 自定义业务控制器示例，展示如何直接注入并使用 {@link OssTemplate}。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class SampleFileController {

    private final OssTemplate ossTemplate;

    @PostMapping("/avatar")
    public R<String> uploadAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        ObjectInfo info = ossTemplate.put().putObject(
                "avatars/", file.getOriginalFilename(), file.getInputStream());
        return R.data(info.getUrl());
    }

    @GetMapping("/list")
    public R<List<ObjectInfo>> listFiles(@RequestParam(defaultValue = "avatars/") String path) {
        return R.data(ossTemplate.query().listObjects(path));
    }

    @GetMapping("/presign")
    public R<String> presign(
            @RequestParam String objectName,
            @RequestParam(defaultValue = "3600") long seconds) {
        String url = ossTemplate.presign()
                .generateGetPresignedUrl(objectName, Duration.ofSeconds(seconds));
        return R.data(url);
    }

    @DeleteMapping
    public R<Void> delete(@RequestParam String objectName) {
        ossTemplate.delete().removeObject(objectName);
        return R.success("删除成功");
    }
}
