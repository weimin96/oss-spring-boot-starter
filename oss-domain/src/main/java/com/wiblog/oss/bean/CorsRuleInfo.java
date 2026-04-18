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
    private String id;
    private List<String> allowedOrigins;
    private List<String> allowedMethods;
    private List<String> allowedHeaders;
    private List<String> exposeHeaders;
    private Integer maxAgeSeconds;
}


