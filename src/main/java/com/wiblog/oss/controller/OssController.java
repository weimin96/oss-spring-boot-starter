package com.wiblog.oss.controller;

import com.wiblog.oss.bean.ObjectTreeNode;
import com.wiblog.oss.bean.chunk.Chunk;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.service.OssTemplate;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * web端点
 * @author panwm
 * @since 2023/8/20 1:38
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("${oss.http.prefix:}/oss")
@Api(tags = "oss:http接口")
@Tag(name = "OssEndpoint", description = "oss:http接口")
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
    @ApiOperation(value = "分片上传大文件")
    public ResponseEntity<String> chunk(@Validated Chunk chunk) {
        ossTemplate.put().chunk(chunk);
        return ResponseEntity.ok("File Chunk Upload Success");
    }

    /**
     * 文件合并
     *
     * @param guid 文件guid
     * @return 响应
     */
    @PostMapping(value = "/merge")
    @ApiOperation(value = "文件合并")
    @ApiImplicitParam(name = "guid", value = "文件唯一id", dataType = "String", paramType = "form")
    public ResponseEntity<ObjectInfo> merge(@RequestParam("guid") String guid) {
        ObjectInfo merge = ossTemplate.put().merge(guid);
        return ResponseEntity.ok(merge);
    }

    /**
     * 获取文件信息
     *
     * @param objectName 文件路径
     * @return 响应
     */
    @GetMapping("/object/{objectName}")
    @ApiOperation(value = "获取文件信息")
    @ApiImplicitParam(name = "objectName", value = "文件全路径", dataType = "String", paramType = "form")
    public ResponseEntity<ObjectInfo> getObject(@PathVariable @NotBlank String objectName) {
        ObjectInfo object = ossTemplate.query().getObject(objectName);
        return ResponseEntity.ok(object);
    }

    /**
     * 获取文件目录
     *
     * @param objectName 文件路径
     * @return 响应
     */
    @GetMapping("/object/tree/{objectName}")
    @ApiOperation(value = "获取文件目录树")
    @ApiImplicitParam(name = "objectName", value = "文件目录", dataType = "String", paramType = "form")
    public ResponseEntity<ObjectTreeNode> getObjectTree(@PathVariable @NotBlank String objectName) {
        ObjectTreeNode tree = ossTemplate.query().getTreeList(objectName);
        return ResponseEntity.ok(tree);
    }

    /**
     * 获取文件件列表
     *
     * @param objectName 目录
     * @return 响应
     */
    @GetMapping("/object/list/{objectName}")
    @ApiOperation(value = "获取文件件列表")
    @ApiImplicitParam(name = "objectName", value = "文件目录", dataType = "String", paramType = "form")
    public ResponseEntity<List<ObjectInfo>> getObjectList(@PathVariable @NotBlank String objectName) {
        List<ObjectInfo> list = ossTemplate.query().getAllObjectsByPrefix(objectName);
        return ResponseEntity.ok(list);
    }

    /**
     * 上传文件
     * @param file 文件
     * @param path 存放路径
     * @return 响应
     * @throws IOException io异常
     */
    @PostMapping(value = "/object/{path}")
    @ApiOperation(value = "上传文件")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "file", value = "文件", dataType = "file", paramType = "form"),
            @ApiImplicitParam(name = "path", value = "存放路径", dataType = "String", paramType = "form")
    })
    public ResponseEntity<ObjectInfo> uploadObject(@RequestBody @NotNull MultipartFile file,
                                                   @PathVariable @NotBlank String path) throws IOException {
        InputStream inputStream = file.getInputStream();
        ObjectInfo objectInfo = ossTemplate.put().putObject(path, file.getName(), inputStream);
        return ResponseEntity.ok(objectInfo);
    }

}
