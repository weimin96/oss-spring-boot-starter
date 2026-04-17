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

    /**
     * 创建一个空响应对象。
     *
     * <p>保留无参构造是为了兼容序列化框架和反射工具，
     * 避免在 Spring MVC / Jackson 处理响应包装时出现实例化限制。</p>
     */
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

    /**
     * 判断响应是否表示成功。
     *
     * <p>这里基于统一成功码而不是 `success` 字段做判断，
     * 是为了让历史响应对象和手工构造结果都遵循同一判定标准。</p>
     *
     * @param result 待判断的响应
     * @return 成功返回 {@code true}
     */
    public static boolean isSuccess(@Nullable OssResponse<?> result) {
        return Optional.ofNullable(result)
                .map(x -> OssOssResultCode.SUCCESS.code == x.code)
                .orElse(Boolean.FALSE);
    }

    /**
     * 判断响应是否表示失败。
     *
     * @param result 待判断的响应
     * @return 失败返回 {@code true}
     */
    public static boolean isNotSuccess(@Nullable OssResponse<?> result) {
        return !isSuccess(result);
    }

    /**
     * 构造一个成功响应，并根据数据是否为空自动选择默认文案。
     *
     * @param data 需要承载的数据
     * @param <T>  数据类型
     * @return 统一响应对象
     */
    @SuppressWarnings("unchecked")
    public static <T> OssResponse<T> data(T data) {
        // 按是否有承载数据区分默认文案，避免调用方重复判断空值。
        return (OssResponse<T>) new OssResponse<>(200, data, data == null ? MSG_NO_DATA : MSG_SUCCESS);
    }

    /**
     * 构造一个带自定义文案的成功响应。
     *
     * @param data 需要承载的数据
     * @param msg  自定义文案；当数据为空时会退化为“暂无承载数据”
     * @param <T>  数据类型
     * @return 统一响应对象
     */
    @SuppressWarnings("unchecked")
    public static <T> OssResponse<T> data(T data, String msg) {
        return (OssResponse<T>) new OssResponse<>(200, data, data == null ? MSG_NO_DATA : msg);
    }

    /**
     * 构造一个自定义状态码的响应。
     *
     * @param code 状态码
     * @param data 承载数据
     * @param msg  返回文案
     * @param <T>  数据类型
     * @return 统一响应对象
     */
    @SuppressWarnings("unchecked")
    public static <T> OssResponse<T> data(int code, T data, String msg) {
        return (OssResponse<T>) new OssResponse<>(code, data, data == null ? MSG_NO_DATA : msg);
    }

    /**
     * 构造一个显式指定成功标记的响应。
     *
     * <p>该重载主要用于兼容部分需要“非 200 但仍视为成功”或“200 但显式失败”的边界场景。</p>
     *
     * @param code    状态码
     * @param data    承载数据
     * @param msg     返回文案
     * @param success 是否成功
     * @param <T>     数据类型
     * @return 统一响应对象
     */
    @SuppressWarnings("unchecked")
    public static <T> OssResponse<T> data(int code, T data, String msg, Boolean success) {
        return (OssResponse<T>) new OssResponse<>(code, data, data == null ? MSG_NO_DATA : msg, success);
    }

    /**
     * 构造一个成功响应。
     *
     * @param msg 成功文案
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> OssResponse<T> success(String msg) {
        return new OssResponse<>(OssOssResultCode.SUCCESS, msg);
    }

    /**
     * 使用结果码构造成功响应。
     *
     * @param resultCode 结果码
     * @param <T>        数据类型
     * @return 成功响应
     */
    public static <T> OssResponse<T> success(IOssResultCode resultCode) {
        return new OssResponse<>(resultCode);
    }

    /**
     * 使用结果码和自定义文案构造成功响应。
     *
     * @param resultCode 结果码
     * @param msg        自定义文案
     * @param <T>        数据类型
     * @return 成功响应
     */
    public static <T> OssResponse<T> success(IOssResultCode resultCode, String msg) {
        return new OssResponse<>(resultCode, msg);
    }

    /**
     * 构造一个失败响应。
     *
     * @param msg 失败文案
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> OssResponse<T> fail(String msg) {
        return new OssResponse<>(OssOssResultCode.FAILURE, msg);
    }

    /**
     * 使用自定义状态码构造失败响应。
     *
     * @param code 状态码
     * @param msg  失败文案
     * @param <T>  数据类型
     * @return 失败响应
     */
    public static <T> OssResponse<T> fail(int code, String msg) {
        return new OssResponse<>(code, null, msg);
    }

    /**
     * 使用结果码构造失败响应。
     *
     * @param resultCode 失败结果码
     * @param <T>        数据类型
     * @return 失败响应
     */
    public static <T> OssResponse<T> fail(IOssResultCode resultCode) {
        return new OssResponse<>(resultCode);
    }

    /**
     * 使用结果码和自定义文案构造失败响应。
     *
     * @param resultCode 失败结果码
     * @param msg        自定义文案
     * @param <T>        数据类型
     * @return 失败响应
     */
    public static <T> OssResponse<T> fail(IOssResultCode resultCode, String msg) {
        return new OssResponse<>(resultCode, msg);
    }

    /**
     * 根据布尔值快速返回成功或失败响应。
     *
     * @param flag 布尔状态
     * @param <T>  数据类型
     * @return 对应状态的统一响应
     */
    public static <T> OssResponse<T> status(boolean flag) {
        return flag ? success(MSG_SUCCESS) : fail(MSG_FAILURE);
    }

    @Override
    public String toString() {
        return "R(code=" + code + ", success=" + success + ", data=" + data + ", msg=" + msg + ")";
    }
}
