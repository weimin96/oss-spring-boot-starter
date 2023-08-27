package com.wiblog.oss.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.constant.ClientEnum;
import com.wiblog.oss.util.Util;

import java.net.MalformedURLException;
import java.net.URL;
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
        return object == null ? null : ObjectInfo.builder()
                .uri(object.getKey())
                .url(getDomain() + object.getKey())
                .name(Util.getFilename(object.getKey()))
                .metadata(object.getObjectMetadata())
                .uploadTime(object.getObjectMetadata().getLastModified())
                .build();
    }

    /**
     * 构造返回结构
     *
     * @param objectName objectName
     * @return ObjectResp
     */
    protected ObjectInfo buildObjectInfo(String objectName) {
        return ObjectInfo.builder()
                .uri(objectName)
                .url(getDomain() + objectName)
                .name(Util.getFilename(objectName))
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
