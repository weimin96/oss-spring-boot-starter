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

    void removeFolder(String path);

    void removeFolder(String bucketName, String path);
}
