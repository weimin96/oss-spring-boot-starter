package com.wiblog.oss.bean;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * 对象字节区间读取命令。
 */
@Value
@Accessors(fluent = true)
public class ReadObjectRangeCommand {
    /**
     * Bucket 名称；为空时使用默认 Bucket。
     */
    String bucket;

    /**
     * 对象 key。
     */
    String key;

    /**
     * 起始字节偏移量，包含该字节。
     */
    long offset;

    /**
     * 最大读取字节数。
     */
    long length;
}
