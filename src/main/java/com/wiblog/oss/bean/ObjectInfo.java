package com.wiblog.oss.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "对象存储文件信息")
public class ObjectInfo {

    @Schema(description = "文件名称")
    protected String name;

    @Schema(description = "对象相对路径")
    protected String uri;

    @Schema(description = "对象访问地址")
    protected String url;

    @Schema(description = "文件大小，单位为字节")
    protected long size;

    @Schema(description = "文件扩展名")
    protected String ext;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "上传时间")
    protected Date uploadTime;
}
