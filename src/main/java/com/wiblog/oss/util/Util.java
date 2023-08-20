package com.wiblog.oss.util;

import com.amazonaws.util.StringUtils;

import java.io.File;

/**
 * 工具类
 * @author panwm
 * @date 2023/8/20 17:50
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
     * 删除文件
     *
     * @param filePath 文件
     * @return
     */
    public static boolean deleteFile(String filePath) {
        boolean flag = false;
        try {
            File file = new File(filePath);
            // 路径为文件且不为空则进行删除
            if (file.isFile() && file.exists()) {
                return file.delete();
            } else if (file.isDirectory() && file.exists()) {
                return deleteDir(file);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return flag;
    }

    /**
     * 递归删除目录下的所有文件及子目录下所有文件
     *
     * @param dir 将要删除的文件目录
     * @return boolean
     */
    private static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                //递归删除目录中的子目录下
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) {
                        return false;
                    }
                }
            }

        }
        // 目录此时为空，可以删除
        return dir.delete();
    }
}
