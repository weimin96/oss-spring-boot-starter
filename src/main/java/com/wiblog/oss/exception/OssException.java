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
        return new OssException("BUCKET_NOT_FOUND", "Bucket not found: " + bucketName);
    }

    public static OssException objectNotFound(String objectName) {
        return new OssException("OBJECT_NOT_FOUND", "Object not found: " + objectName);
    }

    public static OssException uploadFailed(String objectName, Throwable cause) {
        return new OssException("UPLOAD_FAILED", "Upload failed for: " + objectName, cause);
    }

    public static OssException configInvalid(String field) {
        return new OssException("CONFIG_INVALID", "Invalid OSS configuration: " + field);
    }
}
