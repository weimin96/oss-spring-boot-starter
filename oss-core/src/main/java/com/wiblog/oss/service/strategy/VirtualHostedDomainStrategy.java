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

    @Override
    public boolean supports(String type) {
        return type != null && SUPPORTED_TYPES.contains(type.toLowerCase());
    }

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
