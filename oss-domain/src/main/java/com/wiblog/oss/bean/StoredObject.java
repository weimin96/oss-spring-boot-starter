package com.wiblog.oss.bean;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * 已存储对象的稳定标识与完整性信息。
 */
@Value
@Accessors(fluent = true)
public class StoredObject {
    String bucket;
    String key;
    long size;
    String etag;
    String versionId;
    String checksumSha256;
}
