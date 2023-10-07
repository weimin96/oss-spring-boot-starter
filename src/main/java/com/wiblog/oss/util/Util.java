package com.wiblog.oss.util;

import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.util.StringUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

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

    /**
     * 写入文件
     * @param inputStream 输入流
     * @param filePath 文件路径
     */
    public static void copyInputStreamToFile(S3ObjectInputStream inputStream, String filePath) {
        File file = new File(filePath);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        try (OutputStream outputStream = Files.newOutputStream(Paths.get(filePath))) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
