package com.wiblog.oss.support;

/**
 * OSS 自动配置测试用固定属性。
 *
 * <p>这些属性只承载自动配置激活所需的最小输入，
 * 统一收口后可以避免各版本测试在属性清单上重复维护。</p>
 *
 * @author panwm
 */
public final class OssTestProperties {

    private OssTestProperties() {
    }

    /**
     * 构造 Web / OpenAPI 自动配置测试所需的固定属性。
     *
     * @param httpEnabled HTTP 端点开关
     * @return 属性数组
     */
    public static String[] httpProperties(boolean httpEnabled) {
        return new String[]{
                "oss.enable=true",
                "oss.http.enable=" + httpEnabled,
                "oss.endpoint=http://localhost:9000",
                "oss.access-key=ak",
                "oss.secret-key=sk",
                "oss.type=minio",
                "oss.bucket-name=test-bucket"
        };
    }
}
