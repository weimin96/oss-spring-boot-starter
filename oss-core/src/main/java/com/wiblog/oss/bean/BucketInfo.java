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
 * Bucket 基础信息。
 *
 * 这里显式映射对外字段，而不是直接返回 AWS SDK 的 Bucket，
 * 是为了稳定 HTTP 返回协议，并规避 SDK 模型在 Jackson 序列化时的兼容性问题。
 *
 * @author panwm
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "Bucket 基础信息")
public class BucketInfo {

    @Schema(description = "Bucket 名称")
    private String name;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "Bucket 创建时间")
    private Date creationDate;
}
