package com.wiblog.oss.util;

import com.amazonaws.util.StringUtils;

import java.io.File;

/**
 * 工具类
 * @author panwm
 * @since 2023/8/20 17:50
 */
public class Util {

    /**
     * 截取文件名
     * @param path 文件路径
     * @return filename
     */
    public static String getFilename(String path) {
        String[] separators = { "/", "\\" };

        for (String separator : separators) {
            int lastIndex = path.lastIndexOf(separator);
            if (lastIndex > -1) {
                return path.substring(lastIndex + 1);
            }
        }
        return path;
    }

    /**
     * 文件路径适配
     * @param path path
     * @return path
     */
    public static String formatPath(String path) {
        if (StringUtils.isNullOrEmpty(path)) {
            return "";
        }
        path = path.replaceAll("\\\\", "/");
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (!path.endsWith("/")) {
            path += "/";
        }
        return path;
    }

    /**
     * 判断一个路径是否指向文件
     * @param path 路径
     * @return 是否是文件
     */
    public static boolean checkIsFile(String path) {
        if (StringUtils.isNullOrEmpty(path)) {
            return false;
        }
        path = path.replaceAll("\\\\", "/");
        if (path.endsWith("/")) {
            return false;
        }
        return path.substring(path.lastIndexOf("/")).contains(".");
    }
}
