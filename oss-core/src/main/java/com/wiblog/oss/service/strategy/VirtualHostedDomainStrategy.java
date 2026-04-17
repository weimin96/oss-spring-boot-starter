package com.wiblog.oss.service.strategy;

import com.wiblog.oss.exception.OssException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

/**
 * Virtual-Hosted 风格域名策略（适用于 OBS、COS）
 * <p>
 * 格式：{protocol}://{bucketName}.{host}/
 * </p>
 *
 * @author panwm
 */
public class VirtualHostedDomainStrategy implements DomainStrategy {

    /**
     * 使用该策略的 OSS 类型集合
     */
    private static final Set<String> SUPPORTED_TYPES = Collections.unmodifiableSet(
            new java.util.HashSet<>(Arrays.asList("obs", "cos")));

    /**
     * 判断当前 OSS 类型是否应该使用 Virtual-Hosted 域名格式。
     *
     * @param type `oss.type` 配置值
     * @return 支持返回 {@code true}
     */
    @Override
    public boolean supports(String type) {
        return type != null && SUPPORTED_TYPES.contains(type.toLowerCase());
    }

    /**
     * 按 Virtual-Hosted 规则拼接访问域名前缀。
     *
     * <p>这里显式校验 endpoint 是否可解析成 URL，
     * 是为了在配置错误时尽早抛出领域异常，而不是把非法地址拖到后续请求阶段才暴露。</p>
     *
     * @param endpoint   服务端点
     * @param bucketName Bucket 名称
     * @return 形如 `{protocol}://{bucket}.{host}/` 的域名前缀
     */
    @Override
    public String buildDomain(String endpoint, String bucketName) {
        try {
            URL url = new URL(endpoint);
            return url.getProtocol() + "://" + bucketName + "." + url.getHost() + "/";
        } catch (MalformedURLException e) {
            throw new OssException("INVALID_ENDPOINT", "Malformed endpoint URL: " + endpoint, e);
        }
    }
}
