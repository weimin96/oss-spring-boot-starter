package com.wiblog.oss.service;

import com.wiblog.oss.bean.UnzipResult;

/**
 * 流式解压能力端口。
 *
 * @author panwm
 */
public interface OssUnzipService {
    UnzipResult unzip(String zipObjectKey, String targetPath);

    UnzipResult unzip(String sourceBucket, String zipObjectKey, String targetBucket, String targetPath);

    UnzipResult unzip(String zipObjectKey, UnzipEntryHandler handler);

    UnzipResult unzip(String bucketName, String zipObjectKey, UnzipEntryHandler handler);

    UnzipResult unzipWithFilter(String zipObjectKey, String entryPrefix, String targetPath);

    UnzipResult unzipWithFilter(String sourceBucket, String zipObjectKey, String targetBucket,
                                String entryPrefix, String targetPath);
}
