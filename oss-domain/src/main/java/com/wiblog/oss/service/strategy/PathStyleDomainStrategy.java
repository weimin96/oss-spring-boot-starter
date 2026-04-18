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

    /**
     * 声明该策略作为兜底实现始终可用。
     *
     * <p>把 Path-Style 放成最终回退策略的原因是它对 MinIO 和大多数本地兼容环境最稳妥，
     * 即使未识别到具体厂商类型，也能得到一个可工作的默认域名格式。</p>
     *
     * @param type OSS 类型
     * @return 始终返回 {@code true}
     */
    @Override
    public boolean supports(String type) {
        // 作为兜底默认策略，始终返回 true
        return true;
    }

    /**
     * 按 Path-Style 规则拼接访问域名前缀。
     *
     * @param endpoint   服务端点
     * @param bucketName Bucket 名称
     * @return 形如 `{endpoint}/{bucketName}/` 的域名前缀
     */
    @Override
    public String buildDomain(String endpoint, String bucketName) {
        String base = endpoint.endsWith("/") ? endpoint : endpoint + "/";
        return base + bucketName + "/";
    }
}


