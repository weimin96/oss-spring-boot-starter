package com.wiblog.oss.bean;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 流式解压结果。
 *
 * @author panwm
 */
@Data
@Builder
@Schema(description = "流式解压结果")
public class UnzipResult {

    @Schema(description = "解压成功的文件列表")
    private List<ObjectInfo> succeeded;

    @Schema(description = "解压失败的条目名称列表")
    private List<String> failed;

    @Schema(description = "解压的目标路径前缀")
    private String targetPath;

    @Schema(description = "成功数量")
    public int getSucceededCount() {
        return succeeded == null ? 0 : succeeded.size();
    }

    @Schema(description = "失败数量")
    public int getFailedCount() {
        return failed == null ? 0 : failed.size();
    }
}