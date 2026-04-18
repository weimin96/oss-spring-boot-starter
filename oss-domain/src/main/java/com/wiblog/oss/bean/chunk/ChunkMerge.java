package com.wiblog.oss.bean.chunk;

import lombok.Data;

import java.util.List;

/**
 * 分片合并参数。
 *
 * @author panwm
 * @since 2024/8/14 0:18
 */
@Data
public class ChunkMerge {
    private String filename;
    private String path;
    private String uploadId;
    private String guid;
    private List<ChunkTarget> chunkTargetList;
}


