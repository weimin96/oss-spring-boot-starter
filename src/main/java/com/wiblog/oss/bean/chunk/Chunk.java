package com.wiblog.oss.bean.chunk;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;


/**
 * 文件块
 *
 * @author panwm
 * @since 2023/8/20 23:10
 */
@Data
@ApiModel(value = "文件分片上传")
public class Chunk {

    /**
     * 当前文件块，从1开始
     */
    @ApiModelProperty(value = "当前文件块，从1开始", required = true)
    @NotNull
    private Integer chunkNumber;

    /**
     * 文件名
     */
    @ApiModelProperty(value = "文件名", required = true)
    @NotBlank
    private String filename;

    /**
     * 存放路径
     */
    @ApiModelProperty(value = "存放路径", required = true)
    @NotBlank
    private String path;

    /**
     * 分块文件内容
     */
    @ApiModelProperty(value = "分块文件内容", required = true)
    @NotNull
    private MultipartFile file;

    /**
     * 唯一id
     */
    @ApiModelProperty(value = "唯一id", required = true)
    @NotBlank
    private String guid;

}
