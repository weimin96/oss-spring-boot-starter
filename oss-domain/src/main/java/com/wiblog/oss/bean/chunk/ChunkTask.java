package com.wiblog.oss.bean.chunk;

import lombok.Data;

/**
 * 初始化分片上传任务参数。
 *
 * @author panwm
 * @since 2024/8/14 0:14
 */
@Data
public class ChunkTask {
    private String filename;
    private String path;
}


