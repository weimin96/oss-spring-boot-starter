package com.wiblog.oss.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class BucketDetailInfo {
    private String name;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date creationDate;
    private String access;
    private long totalSize;
    private long totalObjectCount;
    private Map<String, String> tags;
}


