package com.wiblog.oss.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * Bucket 基础信息。
 * <p>
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
public class BucketInfo {
    /**
     * 保留 Bucket 名称字段，是为了让列表结果脱离底层 SDK 模型后仍具备稳定主标识。
     */
    private String name;

    /**
     * 统一输出创建时间格式，避免调用方自行处理时区和序列化差异。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date creationDate;
}


