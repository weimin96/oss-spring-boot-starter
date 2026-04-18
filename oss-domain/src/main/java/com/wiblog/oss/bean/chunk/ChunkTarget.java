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
    private Integer partNumber;
    private String etag;
}


