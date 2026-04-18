package com.wiblog.oss.bean;

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
public class UnzipResult {
    /**
     * 成功列表保留完整对象信息，是为了让调用方无需再次查询就能拿到解压产物元数据。
     */
    private List<ObjectInfo> succeeded;

    /**
     * 失败列表只保留条目标识，目的是降低错误结果体体积并便于问题定位。
     */
    private List<String> failed;

    /**
     * 目标路径单独返回，避免调用方在跨目录解压场景下自行推断落点目录。
     */
    private String targetPath;

    /**
     * 统计解压成功的条目数量。
     *
     * <p>这里显式做空值保护，是为了兼容“只记录失败列表”或“只记录成功列表”的部分结果构造过程，
     * 避免调用方还需要额外判断集合是否初始化。</p>
     *
     * @return 成功条目数量
     */
    public int getSucceededCount() {
        return succeeded == null ? 0 : succeeded.size();
    }

    /**
     * 统计解压失败的条目数量。
     *
     * @return 失败条目数量
     */
    public int getFailedCount() {
        return failed == null ? 0 : failed.size();
    }
}


