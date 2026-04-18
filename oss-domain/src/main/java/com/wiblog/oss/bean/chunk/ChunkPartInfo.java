package com.wiblog.oss.bean.chunk;

import lombok.Data;

/**
 * 已上传分片摘要。
 *
 * <p>这里不直接暴露 AWS SDK 的 `Part`，
 * 是为了让领域端口保持纯 Java 契约，
 * 避免调用方因为底层 SDK 变更而被迫联动。</p>
 *
 * @author panwm
 */
@Data
public class ChunkPartInfo {
    private Integer partNumber;
    private String etag;
    private Long size;
}
