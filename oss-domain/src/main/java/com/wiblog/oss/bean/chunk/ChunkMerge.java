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
    /**
     * 文件名单独保留，是为了在合并阶段继续沿用初始化任务时确定的对象命名语义。
     */
    private String filename;

    /**
     * 目标路径显式返回给合并命令，避免服务端依赖隐式默认目录。
     */
    private String path;

    /**
     * uploadId 用于绑定底层多段上传会话，缺失时无法安全识别待合并的分片集合。
     */
    private String uploadId;

    /**
     * guid 作为前端分片任务标识保留，便于把客户端任务和服务端上传会话关联起来。
     */
    private String guid;

    /**
     * 预期分片总数用于识别遗漏的尾部分片，必须与服务端真实分片清单一致。
     */
    private Integer expectedPartCount;

    /**
     * 预期文件总大小用于在合并前校验所有服务端分片的字节总量。
     */
    private Long expectedSize;

    /**
     * 分片摘要列表必须按上传结果传入，合并阶段据此恢复稳定的部件顺序和 ETag 信息。
     */
    private List<ChunkTarget> chunkTargetList;
}


