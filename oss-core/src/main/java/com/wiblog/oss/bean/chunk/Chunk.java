package com.wiblog.oss.bean.chunk;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件分片上传参数。
 *
 * @author panwm
 * @since 2023/8/20 23:10
 */
@Data
@Schema(description = "文件分片上传参数")
public class Chunk {

    @Schema(description = "当前文件块序号，从 1 开始", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer chunkNumber;

    @Schema(description = "文件名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String filename;

    @Schema(description = "存放路径，通过文件 MD5 生成", requiredMode = Schema.RequiredMode.REQUIRED)
    private String path;

    @Schema(description = "文件唯一标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private String guid;

    /**
     * 使用二进制格式声明文件字段，是为了让 OpenAPI 文档能正确渲染上传控件。
     */
    @Schema(description = "分块文件内容", requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "binary")
    private MultipartFile file;

    @Schema(description = "上传任务 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String uploadId;
}
