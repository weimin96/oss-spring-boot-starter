package com.wiblog.oss.bean;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Bucket CORS 规则对外返回模型。
 *
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
@Schema(description = "Bucket CORS 规则")
public class CorsRuleInfo {

    @Schema(description = "规则 ID")
    private String id;

    @Schema(description = "允许的来源列表")
    private List<String> allowedOrigins;

    @Schema(description = "允许的 HTTP 方法列表")
    private List<String> allowedMethods;

    @Schema(description = "允许的请求头列表")
    private List<String> allowedHeaders;

    @Schema(description = "允许暴露给浏览器的响应头列表")
    private List<String> exposeHeaders;

    @Schema(description = "预检请求缓存秒数")
    private Integer maxAgeSeconds;
}
