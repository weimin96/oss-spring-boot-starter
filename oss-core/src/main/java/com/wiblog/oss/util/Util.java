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
     * 截取路径中的文件名。
     *
     * <p>这里先统一分隔符再截取，目的是兼容 Windows 与对象存储 key 的混合写法，
     * 避免调用方传入反斜杠路径时解析出错误结果。</p>
     *
     * @param path 原始路径或对象 key
     * @return 文件名；空白路径时返回原值
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
     *
     * @param path 原始路径或对象 key
     * @return 扩展名；不存在时返回 {@code null}
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
     *
     * @param path 原始路径
     * @return 规范化后的对象 key 或目录前缀
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
     *
     * @param filename 文件名或对象 key
     * @return 推断得到的 MIME 类型
     */
    public static String getContentType(String filename) {
        return TIKA.detect(filename);
    }

    /**
     * 判断路径是否更像一个文件而不是目录。
     *
     * <p>该方法只做轻量规则判断，不访问远端存储；
     * 主要用于在删除、上传等操作里快速区分“文件 key”和“目录前缀”的意图。</p>
     *
     * @param path 原始路径
     * @return 看起来是文件返回 {@code true}
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

    /**
     * 判断字符序列是否为空白。
     *
     * @param cs 待判断字符序列
     * @return 为空、长度为 0 或全是空白字符时返回 {@code true}
     */
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

    /**
     * 安全获取字符序列长度。
     *
     * @param cs 字符序列
     * @return 非空返回实际长度，空值返回 0
     */
    public static int length(CharSequence cs) {
        return cs == null ? 0 : cs.length();
    }
}


