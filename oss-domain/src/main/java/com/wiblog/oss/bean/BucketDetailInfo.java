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
    /**
     * 保留 Bucket 名称字段，是为了让详情页在脱离列表上下文时仍能独立展示标识信息。
     */
    private String name;

    /**
     * 统一使用格式化后的创建时间，避免调用方再处理底层 SDK 时间类型差异。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date creationDate;

    /**
     * 记录当前 Bucket 的访问控制摘要，方便详情查询直接展示权限状态而无需额外拼装 ACL 结果。
     */
    private String access;

    /**
     * 汇总对象总大小，是为了让调用方无需再次遍历对象列表就能评估存储占用。
     */
    private long totalSize;

    /**
     * 汇总对象总数，便于和容量统计一起形成稳定的详情视图。
     */
    private long totalObjectCount;

    /**
     * 直接返回标签键值对，避免调用方暴露底层 SDK 的标签模型。
     */
    private Map<String, String> tags;
}


