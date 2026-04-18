package com.wiblog.oss.bean.chunk;

import lombok.Data;

/**
 * 分片上传命令。
 *
 * <p>领域层只保留与分片上传行为本身相关的元数据和二进制内容，
 * 不感知 `MultipartFile` 这类 Web 容器类型，
 * 这样核心实现既可以被 HTTP 适配调用，也可以被纯 Java 调用复用。</p>
 *
 * @author panwm
 */
@Data
public class ChunkUploadCommand {
    /**
     * 分片编号是服务端识别上传顺序的关键字段，缺失时无法安全参与续传或合并。
     */
    private Integer chunkNumber;

    /**
     * 文件名沿用初始化任务结果，避免各分片请求传入不一致名称导致最终对象漂移。
     */
    private String filename;

    /**
     * 目标路径单独保留，是为了让核心层不依赖 Web 层的路由或表单上下文推断对象位置。
     */
    private String path;

    /**
     * guid 用于把多个 HTTP 分片请求归并到同一个客户端上传任务。
     */
    private String guid;

    /**
     * 二进制内容直接放在命令对象中，是为了隔离 MultipartFile 等 Web 容器类型。
     */
    private byte[] fileBytes;

    /**
     * 内容长度显式传入，便于底层上传前做完整性和性能相关校验。
     */
    private long contentLength;

    /**
     * uploadId 绑定底层多段上传会话，确保每个分片都写入同一对象上传上下文。
     */
    private String uploadId;
}
