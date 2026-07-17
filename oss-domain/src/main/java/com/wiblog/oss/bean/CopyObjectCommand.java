package com.wiblog.oss.bean;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * 对象复制命令。
 */
@Value
@Accessors(fluent = true)
public class CopyObjectCommand {
    String sourceBucket;
    String sourceKey;
    String destinationBucket;
    String destinationKey;

    /**
     * 指定要复制的源对象历史版本。
     *
     * <p>为空时复制当前版本；非空时允许源和目标使用相同 Bucket/key，
     * 用于把历史版本恢复为新的当前版本。</p>
     */
    String sourceVersionId;

    public CopyObjectCommand(String sourceBucket, String sourceKey,
                             String destinationBucket, String destinationKey) {
        this(sourceBucket, sourceKey, destinationBucket, destinationKey, null);
    }

    public CopyObjectCommand(String sourceBucket, String sourceKey,
                             String destinationBucket, String destinationKey,
                             String sourceVersionId) {
        this.sourceBucket = sourceBucket;
        this.sourceKey = sourceKey;
        this.destinationBucket = destinationBucket;
        this.destinationKey = destinationKey;
        this.sourceVersionId = sourceVersionId;
    }
}
