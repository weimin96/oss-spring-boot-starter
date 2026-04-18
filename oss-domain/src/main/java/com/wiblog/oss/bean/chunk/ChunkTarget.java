package com.wiblog.oss.bean.chunk;

import lombok.Data;

/**
 * 单个分片上传结果。
 *
 * @author panwm
 * @since 2024/8/14 15:48
 */
@Data
public class ChunkTarget {
    /**
     * 返回分片序号，是为了让客户端在重试或合并时保持部件顺序稳定。
     */
    private Integer partNumber;

    /**
     * 返回 ETag 供后续合并使用，避免再次回查对象存储获取分片摘要。
     */
    private String etag;
}


