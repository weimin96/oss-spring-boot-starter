package com.wiblog.oss.web.validation;

import com.wiblog.oss.exception.OssException;
import com.wiblog.oss.web.file.OssUploadFile;

import java.util.Collection;
import java.util.Map;

/**
 * Web 请求参数校验工具。
 *
 * <p>共享 Web 契约层统一维护常用参数校验规则，
 * 目的是避免 Boot 2/3/4 控制器各自复制同样的空值与范围判断。</p>
 *
 * @author panwm
 */
public final class OssWebRequestValidator {

    private OssWebRequestValidator() {
    }

    public static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new OssException("INVALID_REQUEST", fieldName + " 不能为空");
        }
        return value;
    }

    public static long requirePositive(long value, String fieldName) {
        if (value < 1) {
            throw new OssException("INVALID_REQUEST", fieldName + " 最小为 1");
        }
        return value;
    }

    public static int requirePositive(int value, String fieldName) {
        if (value < 1) {
            throw new OssException("INVALID_REQUEST", fieldName + " 最小为 1");
        }
        return value;
    }

    public static OssUploadFile requireFile(OssUploadFile file, String fieldName) {
        if (file == null || file.isEmpty()) {
            throw new OssException("INVALID_REQUEST", fieldName + " 不能为空");
        }
        return file;
    }

    public static <T extends Collection<?>> T requireItems(T items, String fieldName) {
        if (items == null || items.isEmpty()) {
            throw new OssException("INVALID_REQUEST", fieldName + " 不能为空");
        }
        return items;
    }

    public static <T extends Map<?, ?>> T requireEntries(T items, String fieldName) {
        if (items == null || items.isEmpty()) {
            throw new OssException("INVALID_REQUEST", fieldName + " 不能为空");
        }
        return items;
    }
}
