package com.wiblog.oss.bean.chunk;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 分片合并参数。
 *
 * @author panwm
 * @since 2024/8/14 0:18
 */
@Data
@Schema(description = "分片合并参数")
public class ChunkMerge {

    @Schema(description = "文件名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String filename;

    @Schema(description = "存放路径，通过文件 MD5 生成", requiredMode = Schema.RequiredMode.REQUIRED)
    private String path;

    @Schema(description = "上传任务 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String uploadId;

    @Schema(description = "文件唯一标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private String guid;

    @Schema(description = "已上传分片结果列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ChunkTarget> chunkTargetList;
}
