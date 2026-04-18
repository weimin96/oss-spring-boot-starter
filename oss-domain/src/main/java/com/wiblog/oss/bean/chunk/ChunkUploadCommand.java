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
    private Integer chunkNumber;
    private String filename;
    private String path;
    private String guid;
    private byte[] fileBytes;
    private long contentLength;
    private String uploadId;
}
