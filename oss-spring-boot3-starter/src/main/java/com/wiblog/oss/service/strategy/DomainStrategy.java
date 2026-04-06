package com.wiblog.oss.service.strategy;

/**
 * 域名构建策略接口
 *
 * @author panwm
 */
public interface DomainStrategy {

    /**
     * 是否支持该 OSS 类型
     *
     * @param type oss.type 配置值
     * @return 是否匹配
     */
    boolean supports(String type);

    /**
     * 构建访问域名前缀（以 "/" 结尾）
     *
     * @param endpoint   端点地址
     * @param bucketName bucket 名称
     * @return 域名前缀
     */
    String buildDomain(String endpoint, String bucketName);
}
