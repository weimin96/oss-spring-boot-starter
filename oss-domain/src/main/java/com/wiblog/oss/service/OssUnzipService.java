package com.wiblog.oss.service;

import com.wiblog.oss.bean.UnzipResult;

/**
 * 流式解压能力端口。
 *
 * <p>实现必须拒绝绝对路径和父目录路径段，并对条目数量、单条目大小和累计解压大小设置上限。</p>
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
