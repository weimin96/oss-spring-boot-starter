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

    /**
     * 创建一个只包含错误码和消息的业务异常。
     *
     * @param code    领域错误码
     * @param message 面向调用方的错误说明
     */
    public OssException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 创建一个带根因的业务异常。
     *
     * <p>保留原始异常的目的是让日志与上层异常处理器都能拿到完整根因，
     * 同时对外仍统一收敛为领域错误码。</p>
     *
     * @param code    领域错误码
     * @param message 面向调用方的错误说明
     * @param cause   原始异常
     */
    public OssException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * 构造“Bucket 不存在”异常。
     *
     * @param bucketName 缺失的 Bucket 名称
     * @return 统一领域异常
     */
    public static OssException bucketNotFound(String bucketName) {
        return new OssException("BUCKET_NOT_FOUND", "未找到 Bucket：" + bucketName);
    }

    /**
     * 构造“对象不存在”异常。
     *
     * @param objectName 缺失的对象 key
     * @return 统一领域异常
     */
    public static OssException objectNotFound(String objectName) {
        return new OssException("OBJECT_NOT_FOUND", "未找到对象：" + objectName);
    }

    /**
     * 构造“上传失败”异常。
     *
     * @param objectName 上传失败的对象 key
     * @param cause      原始失败原因
     * @return 统一领域异常
     */
    public static OssException uploadFailed(String objectName, Throwable cause) {
        return new OssException("UPLOAD_FAILED", "上传失败：" + objectName, cause);
    }

    /**
     * 构造“配置非法”异常。
     *
     * @param field 配置项名称
     * @return 统一领域异常
     */
    public static OssException configInvalid(String field) {
        return new OssException("CONFIG_INVALID", "OSS 配置无效：" + field);
    }
}
