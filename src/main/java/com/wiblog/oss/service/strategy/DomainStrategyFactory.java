package com.wiblog.oss.service.strategy;

import java.util.List;

/**
 * 域名策略工厂
 * <p>
 * 新增 OSS 类型只需添加新的 DomainStrategy 实现并注册即可。
 * </p>
 *
 * @author panwm
 */
public class DomainStrategyFactory {

    /**
     * 有序策略列表：优先级高的放前面，PathStyle 作为默认兜底放最后
     */
    private static final List<DomainStrategy> STRATEGIES = List.of(
            new VirtualHostedDomainStrategy(),
            new PathStyleDomainStrategy()   // 兜底
    );

    private DomainStrategyFactory() {
    }

    /**
     * 根据 OSS 类型选取匹配的策略
     *
     * @param type OSS 类型，如 "obs" / "cos" / "minio"
     * @return 匹配的域名策略（不会返回 null）
     */
    public static DomainStrategy getStrategy(String type) {
        return STRATEGIES.stream()
                .filter(s -> s.supports(type))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No domain strategy found for type: " + type));
    }
}
