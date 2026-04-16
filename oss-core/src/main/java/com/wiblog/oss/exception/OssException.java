package com.wiblog.oss.exception;

import lombok.Getter;

/**
 * OSS 统一异常类
 *
 * @author panwm
 */
@Getter
public class OssException extends RuntimeException {

    private final String code;

    public OssException(String code, String message) {
        super(message);
        this.code = code;
    }

    public OssException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public static OssException bucketNotFound(String bucketName) {
        return new OssException("BUCKET_NOT_FOUND", "未找到 Bucket：" + bucketName);
    }

    public static OssException objectNotFound(String objectName) {
        return new OssException("OBJECT_NOT_FOUND", "未找到对象：" + objectName);
    }

    public static OssException uploadFailed(String objectName, Throwable cause) {
        return new OssException("UPLOAD_FAILED", "上传失败：" + objectName, cause);
    }

    public static OssException configInvalid(String field) {
        return new OssException("CONFIG_INVALID", "OSS 配置无效：" + field);
    }
}
