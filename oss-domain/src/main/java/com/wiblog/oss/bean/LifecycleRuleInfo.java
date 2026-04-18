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
    private String id;
    private String status;
    private String prefix;
    private Integer expirationDays;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expirationDate;
    private Boolean expiredObjectDeleteMarker;
}


