package com.wiblog.oss.service;

import java.util.List;

/**
 * 删除能力端口。
 *
 * @author panwm
 */
public interface OssDeleteService {
    void removeObject(String objectName);

    void removeObject(String bucketName, String objectName);

    void removeObjects(List<String> objectNames);

    void removeObjects(String bucketName, List<String> objectNames);

    /**
     * 递归删除默认 Bucket 下的非空目录前缀。
     *
     * <p>目录前缀会无条件补充尾部斜杠；空前缀会被拒绝，避免误删整个 Bucket。</p>
     */
    void removeFolder(String path);

    /**
     * 递归删除指定 Bucket 下的非空目录前缀。
     */
    void removeFolder(String bucketName, String path);
}
