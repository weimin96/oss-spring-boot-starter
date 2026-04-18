package com.wiblog.oss.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Bucket 按时间回滚结果。
 * <p>
 * 回滚的目标是把“当前最新可见状态”调整到目标时间点对应的版本视图，
 * 因此这里返回恢复、删除和跳过的对象数量，便于调用方评估回滚影响。
 *
 * @author panwm
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class BucketRewindResult {
    /**
     * 显式返回目标 Bucket，是为了让回滚结果在日志、审计和批量操作场景下可以独立追踪。
     */
    private String bucketName;

    /**
     * 记录调用方请求的回滚时间点，便于和实际执行结果进行核对。
     */
    private String targetTime;

    /**
     * 扫描对象总数反映本次回滚评估范围，避免调用方只看到变更数而误判影响面。
     */
    private long scannedObjectCount;

    /**
     * 恢复对象数量用于表示从历史版本恢复为当前可见状态的条目数。
     */
    private long restoredObjectCount;

    /**
     * 删除对象数量用于表示需要通过删除当前版本才能回到目标时间点的条目数。
     */
    private long deletedObjectCount;

    /**
     * 跳过对象数量保留未处理条目统计，便于调用方评估未命中或无需调整的情况。
     */
    private long skippedObjectCount;
}


