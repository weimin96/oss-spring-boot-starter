package com.wiblog.oss.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * Bucket 生命周期规则对外返回模型。
 *
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
@Schema(description = "Bucket 生命周期规则")
public class LifecycleRuleInfo {

    @Schema(description = "规则 ID")
    private String id;

    @Schema(description = "规则状态")
    private String status;

    @Schema(description = "作用路径前缀")
    private String prefix;

    @Schema(description = "按天数过期时的天数")
    private Integer expirationDays;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "按日期过期时的时间点")
    private Date expirationDate;

    @Schema(description = "是否清理过期删除标记")
    private Boolean expiredObjectDeleteMarker;
}
