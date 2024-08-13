package com.wiblog.oss.util;


import org.apache.tika.Tika;

/**
 * 工具类
 *
 * @author panwm
 * @since 2023/8/20 17:50
 */
public class Util {

    /**
     * 截取文件名
     *
     * @param path 文件路径
     * @return filename
     */
    public static String getFilename(String path) {
        String[] separators = {"/", "\\"};

        for (String separator : separators) {
            int lastIndex = path.lastIndexOf(separator);
            if (lastIndex > -1) {
                return path.substring(lastIndex + 1);
            }
        }
        return path;
    }

    /**
     * 截取文件拓展名
     *
     * @param path 文件路径
     * @return ext
     */
    public static String getExtension(String path) {
        int lastIndex = path.lastIndexOf('.');
        if (lastIndex != -1 && lastIndex < path.length() - 1) {
            return path.substring(lastIndex + 1);
        } else {
            return null;
        }
    }

    /**
     * 文件路径适配
     *
     * @param path path
     * @return path
     */
    public static String formatPath(String path) {
        if (isBlank(path) || "/".equals(path)) {
            return "";
        }
        path = path.replaceAll("\\\\", "/");
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (!path.endsWith("/") && !path.split("/")[path.split("/").length - 1].contains(".")) {
            path += "/";
        }
        return path;
    }

    /**
     * 获取文件内容类型
     *
     * @param filename 文件名
     * @return 文件内容类型
     */
    public static String getContentType(String filename) {
        Tika tika = new Tika();
        return tika.detect(filename);
    }

    /**
     * 判断一个路径是否指向文件
     *
     * @param path 路径
     * @return 是否是文件
     */
    public static boolean checkIsFile(String path) {
        if (isBlank(path)) {
            return false;
        }
        path = path.replaceAll("\\\\", "/");
        if (path.endsWith("/")) {
            return false;
        }
        return path.substring(path.lastIndexOf("/")).contains(".");
    }

    public static boolean isBlank(CharSequence cs) {
        int strLen = length(cs);
        if (strLen == 0) {
            return true;
        } else {
            for (int i = 0; i < strLen; ++i) {
                if (!Character.isWhitespace(cs.charAt(i))) {
                    return false;
                }
            }

            return true;
        }
    }

    public static int length(CharSequence cs) {
        return cs == null ? 0 : cs.length();
    }
}
