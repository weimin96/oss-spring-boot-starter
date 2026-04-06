package com.wiblog.oss.bean.chunk;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 单个分片上传结果。
 *
 * @author panwm
 * @since 2024/8/14 15:48
 */
@Data
@Schema(description = "单个分片上传结果")
public class ChunkTarget {

    @Schema(description = "分片序号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer partNumber;

    @Schema(description = "分片 ETag", requiredMode = Schema.RequiredMode.REQUIRED)
    private String etag;
}
