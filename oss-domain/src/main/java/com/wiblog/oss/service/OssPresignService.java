package com.wiblog.oss.service;

import java.time.Duration;
import java.util.Map;

/**
 * 预签名能力端口。
 *
 * @author panwm
 */
public interface OssPresignService {
    String generateGetPresignedUrl(String objectName);

    String generateGetPresignedUrl(String objectName, Duration expiration);

    String generateGetPresignedUrl(String bucketName, String objectName, Duration expiration);

    String generatePutPresignedUrl(String objectName, String contentType);

    String generatePutPresignedUrl(String objectName, String contentType, Duration expiration, Map<String, String> metadata);

    String generatePutPresignedUrl(String bucketName, String objectName, String contentType,
                                   Duration expiration, Map<String, String> metadata);
}
