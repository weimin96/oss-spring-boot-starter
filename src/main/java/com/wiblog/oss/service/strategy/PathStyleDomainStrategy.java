package com.wiblog.oss.service.strategy;

/**
 * Path-Style 风格域名策略（默认，适用于 MinIO、标准 S3）
 * <p>
 * 格式：{endpoint}/{bucketName}/
 * </p>
 *
 * @author panwm
 */
public class PathStyleDomainStrategy implements DomainStrategy {

    @Override
    public boolean supports(String type) {
        // 作为兜底默认策略，始终返回 true
        return true;
    }

    @Override
    public String buildDomain(String endpoint, String bucketName) {
        String base = endpoint.endsWith("/") ? endpoint : endpoint + "/";
        return base + bucketName + "/";
    }
}
