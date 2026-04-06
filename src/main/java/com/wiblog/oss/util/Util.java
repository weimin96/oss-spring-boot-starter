package com.wiblog.oss.util;

import org.apache.tika.Tika;

/**
 * 工具类
 *
 * @author panwm
 */
public final class Util {

    /**
     * 单例：Tika 是线程安全的，避免每次调用都重新初始化
     */
    private static final Tika TIKA = new Tika();

    private Util() {
    }

    /**
     * 截取文件名。
     * 改进：先统一分隔符，再截取，避免混合路径截取错误。
     */
    public static String getFilename(String path) {
        if (isBlank(path)) {
            return path;
        }
        String normalized = path.replace('\\', '/');
        int lastIndex = normalized.lastIndexOf('/');
        return lastIndex >= 0 ? normalized.substring(lastIndex + 1) : normalized;
    }

    /**
     * 截取文件扩展名（不含 "."）。
     */
    public static String getExtension(String path) {
        if (isBlank(path)) {
            return null;
        }
        int dotIndex = path.lastIndexOf('.');
        int slashIndex = path.lastIndexOf('/');
        // 点必须在最后一个 "/" 之后，且不是末尾字符
        if (dotIndex > slashIndex && dotIndex < path.length() - 1) {
            return path.substring(dotIndex + 1);
        }
        return null;
    }

    /**
     * 路径规范化：
     * - null / 空 / "/" → 返回 ""
     * - 去掉开头的 "/"
     * - 目录路径（末尾无扩展名）确保以 "/" 结尾
     * 改进：减少 String.split() 调用，改用 lastIndexOf 判断。
     */
    public static String formatPath(String path) {
        if (isBlank(path) || "/".equals(path)) {
            return "";
        }
        path = path.replace('\\', '/');
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        // 判断最后一段是否含扩展名（含 "." 则视为文件，否则视为目录补 "/"）
        if (!path.endsWith("/")) {
            String lastSegment = path.substring(path.lastIndexOf('/') + 1);
            if (!lastSegment.contains(".")) {
                path += "/";
            }
        }
        return path;
    }

    /**
     * 获取 MIME 类型。
     * 改进：使用静态单例 Tika，原代码每次 new Tika()。
     */
    public static String getContentType(String filename) {
        return TIKA.detect(filename);
    }

    /**
     * 判断路径是否指向一个文件（含扩展名且不以 "/" 结尾）。
     */
    public static boolean checkIsFile(String path) {
        if (isBlank(path)) {
            return false;
        }
        path = path.replace('\\', '/');
        if (path.endsWith("/")) {
            return false;
        }
        String lastSegment = path.substring(path.lastIndexOf('/') + 1);
        return lastSegment.contains(".");
    }

    public static boolean isBlank(CharSequence cs) {
        int len = length(cs);
        if (len == 0) {
            return true;
        }
        for (int i = 0; i < len; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static int length(CharSequence cs) {
        return cs == null ? 0 : cs.length();
    }
}
