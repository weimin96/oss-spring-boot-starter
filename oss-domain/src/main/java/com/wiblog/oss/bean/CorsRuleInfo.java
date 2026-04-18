package com.wiblog.oss.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Bucket CORS 规则对外返回模型。
 * <p>
 * 这里不直接返回 AWS SDK 的 CORSRule，
 * 是为了让前端始终拿到稳定、可序列化的领域字段，
 * 同时避免把 SDK 内部实现细节暴露到接口协议中。
 *
 * @author panwm
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class CorsRuleInfo {
    /**
     * 保留规则 ID，是为了让调用方可以稳定定位并编辑同一条 CORS 规则。
     */
    private String id;

    /**
     * 允许来源列表直接映射到领域对象，避免前端再处理底层 SDK 的集合包装。
     */
    private List<String> allowedOrigins;

    /**
     * 允许方法列表保持字符串形式，目的是兼容现有接口协议和不同对象存储厂商的枚举差异。
     */
    private List<String> allowedMethods;

    /**
     * 允许请求头列表用于显式表达预检约束，避免跨域失败只能依赖底层错误排查。
     */
    private List<String> allowedHeaders;

    /**
     * 暴露响应头列表单独返回，是为了让调用方明确哪些头可以被浏览器读取。
     */
    private List<String> exposeHeaders;

    /**
     * 最大缓存秒数使用整数表示，便于和 HTTP CORS 协议字段直接对齐。
     */
    private Integer maxAgeSeconds;
}


