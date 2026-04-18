package com.wiblog.oss.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Bucket ACL 信息。
 * <p>
 * 这里对外只暴露 S3 默认 canned ACL 名称，
 * 目的是让前端和调用方围绕稳定的权限枚举交互，
 * 而不是直接耦合到底层 grants 明细结构。
 *
 * @author panwm
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class BucketAccessInfo {
    /**
     * 显式携带 Bucket 名称，是为了让 ACL 结果在批量查询或异步消费时不依赖额外上下文。
     */
    private String bucketName;

    /**
     * 只暴露 canned ACL 名称，目的是保持前后端协议稳定，不把 grants 明细泄漏到领域边界外。
     */
    private String acl;

    /**
     * 标记底层对象存储是否支持 ACL 能力，避免把“不支持”和“查询失败”混为同一类结果。
     */
    private boolean supported;

    /**
     * 当 ACL 能力受限或查询失败时返回诊断信息，便于调用方决定是否降级展示。
     */
    private String message;
}


