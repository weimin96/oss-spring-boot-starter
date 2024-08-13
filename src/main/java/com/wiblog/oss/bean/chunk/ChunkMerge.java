package com.wiblog.oss.bean.chunk;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * @author panwm
 * @since 2024/8/14 0:18
 */
@Data
public class ChunkMerge {

    /**
     * 文件名
     */
    @ApiModelProperty(value = "文件名", required = true)
    @NotBlank
    private String filename;

    /**
     * 存放路径
     */
    @ApiModelProperty(value = "存放路径（通过文件MD5生成）", required = true)
    @NotBlank
    private String path;

    /**
     * oss 分片上传uploadId
     */
    @ApiModelProperty(value = "上传任务id", required = true)
    @NotBlank
    private String uploadId;
}
