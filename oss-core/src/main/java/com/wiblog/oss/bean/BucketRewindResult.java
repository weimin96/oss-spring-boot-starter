package com.wiblog.oss.bean;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
/**
 * Bucket 按时间回滚结果。
 *
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
@Schema(description = "Bucket 按时间回滚结果")
public class BucketRewindResult {

    @Schema(description = "Bucket 名称")
    private String bucketName;

    @Schema(description = "回滚目标 UTC 时间，使用 ISO-8601 表示")
    private String targetTime;

    @Schema(description = "扫描到的对象数量")
    private long scannedObjectCount;

    @Schema(description = "恢复到历史版本的对象数量")
    private long restoredObjectCount;

    @Schema(description = "通过删除当前版本隐藏的对象数量")
    private long deletedObjectCount;

    @Schema(description = "无需处理的对象数量")
    private long skippedObjectCount;
}
