package com.wiblog.oss.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * Bucket 生命周期规则对外返回模型。
 * <p>
 * 这里显式抽出领域对象，而不是直接暴露 AWS SDK 的 LifecycleRule，
 * 是为了稳定 HTTP 返回协议，并规避 SDK 模型在 Jackson 序列化时的兼容性问题。
 *
 * @author panwm
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class LifecycleRuleInfo {
    /**
     * 保留生命周期规则 ID，是为了让调用方在更新和审计时能稳定定位规则。
     */
    private String id;

    /**
     * 规则状态保持字符串形式，目的是兼容不同厂商返回值并避免引入额外枚举适配。
     */
    private String status;

    /**
     * 前缀约束单独暴露，便于调用方判断规则作用范围而无需解析原始过滤表达式。
     */
    private String prefix;

    /**
     * 以天数表示过期条件，适合直接映射常见生命周期配置场景。
     */
    private Integer expirationDays;

    /**
     * 当规则按绝对时间过期时返回该值，避免调用方把天数规则和日期规则混淆。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expirationDate;

    /**
     * 删除标记策略单独保留，是为了显式区分“删除对象版本”和“清理删除标记”这两类语义。
     */
    private Boolean expiredObjectDeleteMarker;
}


