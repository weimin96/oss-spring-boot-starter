package com.wiblog.oss.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.Map;

/**
 * Bucket 详情信息。
 * <p>
 * 这是面向前端查询页面的稳定聚合视图，
 * 聚合了名称、创建时间、ACL、当前对象统计和标签，
 * 避免调用方自己拼装多个接口结果。
 *
 * @author panwm
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "Bucket 详情信息")
public class BucketDetailInfo {

    @Schema(description = "Bucket 名称")
    private String name;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private Date creationDate;

    @Schema(description = "S3 默认 ACL 名称，如 private、public-read")
    private String access;

    @Schema(description = "当前对象总大小，单位字节")
    private long totalSize;

    @Schema(description = "当前对象总数")
    private long totalObjectCount;

    @Schema(description = "Bucket 标签")
    private Map<String, String> tags;
}
