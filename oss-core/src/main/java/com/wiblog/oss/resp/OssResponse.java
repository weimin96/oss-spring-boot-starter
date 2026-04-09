package com.wiblog.oss.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.Optional;

/**
 * 统一响应包装类。
 *
 * @author panwm
 */
@Setter
@Getter
@Schema(description = "统一响应")
public class OssResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String MSG_SUCCESS = "操作成功";
    private static final String MSG_FAILURE = "操作失败";
    private static final String MSG_NO_DATA = "暂无承载数据";

    @Schema(description = "状态码", requiredMode = Schema.RequiredMode.REQUIRED)
    private int code;

    @Schema(description = "是否成功", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean success;

    @Schema(description = "承载数据")
    private T data;

    @Schema(description = "返回消息", requiredMode = Schema.RequiredMode.REQUIRED)
    private String msg;

    public OssResponse() {
    }

    private OssResponse(IOssResultCode resultCode) {
        this(resultCode, null, resultCode.getMessage());
    }

    private OssResponse(IOssResultCode resultCode, String msg) {
        this(resultCode, null, msg);
    }

    private OssResponse(IOssResultCode resultCode, T data) {
        this(resultCode, data, resultCode.getMessage());
    }

    private OssResponse(IOssResultCode resultCode, T data, String msg) {
        this(resultCode.getCode(), data, msg);
    }

    private OssResponse(int code, T data, String msg) {
        this.code = code;
        this.data = data;
        this.msg = msg;
        this.success = OssOssResultCode.SUCCESS.code == code;
    }

    private OssResponse(int code, T data, String msg, boolean success) {
        this.code = code;
        this.data = data;
        this.msg = msg;
        this.success = success;
    }

    public static boolean isSuccess(@Nullable OssResponse<?> result) {
        return Optional.ofNullable(result)
                .map(x -> OssOssResultCode.SUCCESS.code == x.code)
                .orElse(Boolean.FALSE);
    }

    public static boolean isNotSuccess(@Nullable OssResponse<?> result) {
        return !isSuccess(result);
    }

    @SuppressWarnings("unchecked")
    public static <T> OssResponse<T> data(T data) {
        // 按是否有承载数据区分默认文案，避免调用方重复判断空值。
        return (OssResponse<T>) new OssResponse<>(200, data, data == null ? MSG_NO_DATA : MSG_SUCCESS);
    }

    @SuppressWarnings("unchecked")
    public static <T> OssResponse<T> data(T data, String msg) {
        return (OssResponse<T>) new OssResponse<>(200, data, data == null ? MSG_NO_DATA : msg);
    }

    @SuppressWarnings("unchecked")
    public static <T> OssResponse<T> data(int code, T data, String msg) {
        return (OssResponse<T>) new OssResponse<>(code, data, data == null ? MSG_NO_DATA : msg);
    }

    @SuppressWarnings("unchecked")
    public static <T> OssResponse<T> data(int code, T data, String msg, Boolean success) {
        return (OssResponse<T>) new OssResponse<>(code, data, data == null ? MSG_NO_DATA : msg, success);
    }

    public static <T> OssResponse<T> success(String msg) {
        return new OssResponse<>(OssOssResultCode.SUCCESS, msg);
    }

    public static <T> OssResponse<T> success(IOssResultCode resultCode) {
        return new OssResponse<>(resultCode);
    }

    public static <T> OssResponse<T> success(IOssResultCode resultCode, String msg) {
        return new OssResponse<>(resultCode, msg);
    }

    public static <T> OssResponse<T> fail(String msg) {
        return new OssResponse<>(OssOssResultCode.FAILURE, msg);
    }

    public static <T> OssResponse<T> fail(int code, String msg) {
        return new OssResponse<>(code, null, msg);
    }

    public static <T> OssResponse<T> fail(IOssResultCode resultCode) {
        return new OssResponse<>(resultCode);
    }

    public static <T> OssResponse<T> fail(IOssResultCode resultCode, String msg) {
        return new OssResponse<>(resultCode, msg);
    }

    public static <T> OssResponse<T> status(boolean flag) {
        return flag ? success(MSG_SUCCESS) : fail(MSG_FAILURE);
    }

    @Override
    public String toString() {
        return "R(code=" + code + ", success=" + success + ", data=" + data + ", msg=" + msg + ")";
    }
}
