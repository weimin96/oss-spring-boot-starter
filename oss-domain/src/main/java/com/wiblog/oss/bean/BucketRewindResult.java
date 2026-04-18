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
    private String bucketName;
    private String targetTime;
    private long scannedObjectCount;
    private long restoredObjectCount;
    private long deletedObjectCount;
    private long skippedObjectCount;
}


