package com.wiblog.oss.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.io.Serial;
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
public class R<T> implements Serializable {

    @Serial
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

    public R() {
    }

    private R(IResultCode resultCode) {
        this(resultCode, null, resultCode.getMessage());
    }

    private R(IResultCode resultCode, String msg) {
        this(resultCode, null, msg);
    }

    private R(IResultCode resultCode, T data) {
        this(resultCode, data, resultCode.getMessage());
    }

    private R(IResultCode resultCode, T data, String msg) {
        this(resultCode.getCode(), data, msg);
    }

    private R(int code, T data, String msg) {
        this.code = code;
        this.data = data;
        this.msg = msg;
        this.success = ResultCode.SUCCESS.code == code;
    }

    private R(int code, T data, String msg, boolean success) {
        this.code = code;
        this.data = data;
        this.msg = msg;
        this.success = success;
    }

    public static boolean isSuccess(@Nullable R<?> result) {
        return Optional.ofNullable(result)
                .map(x -> ResultCode.SUCCESS.code == x.code)
                .orElse(Boolean.FALSE);
    }

    public static boolean isNotSuccess(@Nullable R<?> result) {
        return !isSuccess(result);
    }

    @SuppressWarnings("unchecked")
    public static <T> R<T> data(T data) {
        // 按是否有承载数据区分默认文案，避免调用方重复判断空值。
        return (R<T>) new R<>(200, data, data == null ? MSG_NO_DATA : MSG_SUCCESS);
    }

    @SuppressWarnings("unchecked")
    public static <T> R<T> data(T data, String msg) {
        return (R<T>) new R<>(200, data, data == null ? MSG_NO_DATA : msg);
    }

    @SuppressWarnings("unchecked")
    public static <T> R<T> data(int code, T data, String msg) {
        return (R<T>) new R<>(code, data, data == null ? MSG_NO_DATA : msg);
    }

    @SuppressWarnings("unchecked")
    public static <T> R<T> data(int code, T data, String msg, Boolean success) {
        return (R<T>) new R<>(code, data, data == null ? MSG_NO_DATA : msg, success);
    }

    public static <T> R<T> success(String msg) {
        return new R<>(ResultCode.SUCCESS, msg);
    }

    public static <T> R<T> success(IResultCode resultCode) {
        return new R<>(resultCode);
    }

    public static <T> R<T> success(IResultCode resultCode, String msg) {
        return new R<>(resultCode, msg);
    }

    public static <T> R<T> fail(String msg) {
        return new R<>(ResultCode.FAILURE, msg);
    }

    public static <T> R<T> fail(int code, String msg) {
        return new R<>(code, null, msg);
    }

    public static <T> R<T> fail(IResultCode resultCode) {
        return new R<>(resultCode);
    }

    public static <T> R<T> fail(IResultCode resultCode, String msg) {
        return new R<>(resultCode, msg);
    }

    public static <T> R<T> status(boolean flag) {
        return flag ? success(MSG_SUCCESS) : fail(MSG_FAILURE);
    }

    @Override
    public String toString() {
        return "R(code=" + code + ", success=" + success + ", data=" + data + ", msg=" + msg + ")";
    }
}
