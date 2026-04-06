package com.wiblog.oss.bean.chunk;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 初始化分片上传任务参数。
 *
 * @author panwm
 * @since 2024/8/14 0:14
 */
@Data
@Schema(description = "初始化分片上传任务参数")
public class ChunkTask {

    @Schema(description = "文件名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String filename;

    @Schema(description = "存放路径，通过文件 MD5 生成", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String path;
}
