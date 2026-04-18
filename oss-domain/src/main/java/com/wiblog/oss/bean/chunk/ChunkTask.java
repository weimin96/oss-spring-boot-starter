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
    /**
     * 文件名在初始化阶段先固定下来，是为了让后续每个分片命令共享同一对象命名结果。
     */
    private String filename;

    /**
     * 目标路径需要在任务创建时声明，避免分片上传和最终合并落到不同目录。
     */
    private String path;
}


