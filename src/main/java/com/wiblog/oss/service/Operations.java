package com.wiblog.oss.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;
import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.constant.ClientEnum;
import com.wiblog.oss.util.Util;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.Optional;

/**
 * @author panwm
 * @since 2023/8/22 0:26
 */
public abstract class Operations {

    protected final OssProperties ossProperties;

    protected final AmazonS3 amazonS3;

    public Operations(OssProperties ossProperties, AmazonS3 amazonS3) {
        this.ossProperties = ossProperties;
        this.amazonS3 = amazonS3;
    }

    public OssProperties getOssProperties() {
        return ossProperties;
    }

    public AmazonS3 getAmazonS3() {
        return amazonS3;
    }

    /**
     * 构造返回结构
     *
     * @param object object
     * @return ObjectResp
     */
    protected ObjectInfo buildObjectInfo(S3Object object) {
        ObjectInfo result = null;
        try {
            result = object == null ? null : ObjectInfo.builder()
                    .uri(object.getKey())
                    .url(getDomain() + object.getKey())
                    .name(Util.getFilename(object.getKey()))
                    .uploadTime(object.getObjectMetadata().getLastModified())
                    .size(object.getObjectContent().available())
                    .ext(Util.getExtension(object.getKey()))
                    .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (object != null) {
            try {
                object.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    protected ObjectTreeNode buildObjectInfo(S3ObjectSummary object) {
        return object == null ? null :
                new ObjectTreeNode(Util.getFilename(object.getKey()), object.getKey(),
                        getDomain() + object.getKey(), object.getLastModified(), "file", object.getSize(), Util.getExtension(object.getKey()));
    }

    /**
     * 构造返回结构
     *
     * @param objectName objectName
     * @return ObjectResp
     */
    protected ObjectTreeNode buildTreeNode(String objectName) {
        if (objectName.endsWith("/")) {
            objectName = objectName.substring(0, objectName.length() - 1);
        }
        return new ObjectTreeNode(Util.getFilename(objectName), objectName, getDomain() + objectName, null, "folder", 0, null);
    }

    /**
     * 构造返回结构
     * @param objectName objectName
     * @param fileSize fileSize
     * @return ObjectInfo
     */
    protected ObjectInfo buildObjectInfo(String objectName, long fileSize) {
        if (objectName.endsWith("/")) {
            objectName = objectName.substring(0, objectName.length() - 1);
        }
        return ObjectInfo.builder()
                .uri(objectName)
                .url(getDomain() + objectName)
                .size(fileSize)
                .ext(Util.getExtension(objectName))
                .name(Util.getFilename(objectName))
                .uploadTime(new Date())
                .build();
    }

    protected String getDomain() {
        if (ClientEnum.OBS.getType().equals(ossProperties.getType())) {
            URL url;
            try {
                url = new URL(ossProperties.getEndpoint());
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
            return url.getProtocol() + "://" + ossProperties.getBucketName() + "." + url.getHost() + "/";
        } else {
            return ossProperties.getEndpoint() + "/" + ossProperties.getBucketName() + "/";
        }
    }
}
