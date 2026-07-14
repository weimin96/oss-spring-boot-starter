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
}
