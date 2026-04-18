package com.wiblog.oss.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * 对象存储文件信息。
 *
 * @author panwm
 * @since 2023/8/20 17:09
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class ObjectInfo {
    /**
     * 名称字段直接返回，避免调用方每次都从对象键中反向截取展示名称。
     */
    protected String name;

    /**
     * 保存对象在存储中的完整路径，是为了让后续下载、预览和删除操作复用统一标识。
     */
    protected String uri;

    /**
     * 对外访问地址与对象键分离，便于兼容不同域名策略和签名方式。
     */
    protected String url;

    /**
     * 类型保持字符串而非枚举，是为了兼容历史协议并减少跨模块联动修改。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    protected String type;

    /**
     * 文件大小统一按字节返回，便于调用方做容量统计和下载提示。
     */
    protected long size;

    /**
     * 扩展名单独缓存，避免前端或调用方重复解析名称并处理多点号文件名。
     */
    protected String ext;

    /**
     * 上传时间统一抽象到领域对象中，屏蔽底层对象存储返回字段差异。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    protected Date uploadTime;
}


