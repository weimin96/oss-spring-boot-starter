package com.wiblog.oss.bean;

import lombok.Value;
import lombok.experimental.Accessors;

import java.io.InputStream;
import java.util.Map;

/**
 * 对象上传命令。
 *
 * <p>使用不可变类和 fluent 访问器模拟 record 的公开契约，同时保持 Java 8 兼容。</p>
 */
@Value
@Accessors(fluent = true)
public class PutObjectCommand {
    String bucket;
    String key;
    InputStream input;
    Long contentLength;
    String contentType;
    Map<String, String> metadata;
    Map<String, String> tags;
    String checksumSha256;
    boolean createOnly;
}
