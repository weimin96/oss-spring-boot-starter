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
    /**
     * 分片序号保留下来，是为了让续传和合并流程可以稳定识别各个部件的位置。
     */
    private Integer partNumber;

    /**
     * ETag 作为分片完整性摘要返回，合并请求需要依赖它和对象存储进行一致性校验。
     */
    private String etag;

    /**
     * 分片大小独立暴露，便于调用方核对上传进度和异常分片。
     */
    private Long size;
}
