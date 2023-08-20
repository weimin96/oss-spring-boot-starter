package com.wiblog.oss.controller;

import com.wiblog.oss.bean.Chunk;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.service.OssTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * web端点
 * @author panwm
 * @date 2023/8/20 1:38
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("${oss.http.prefix:}/oss")
public class OssController {

    /**
     * OSS操作模板
     */
    private final OssTemplate ossTemplate;

    /**
     * 分块上传文件
     *
     * @param chunk 文件块信息
     * @return 响应
     */
    @PostMapping(value = "/chunk")
    public ResponseEntity<String> chunk(Chunk chunk) {
        ossTemplate.put().chunk(chunk);
        return ResponseEntity.ok("File Chunk Upload Success");
    }

    /**
     * 文件合并
     *
     * @param filename 文件名
     * @return 响应
     */
    @GetMapping(value = "/merge")
    public ResponseEntity<Void> merge(@RequestParam("filename") String filename) {
        ossTemplate.put().merge(filename);
        return ResponseEntity.ok().build();
    }

}
