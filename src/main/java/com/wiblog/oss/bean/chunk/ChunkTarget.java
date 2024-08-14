package com.wiblog.oss.bean.chunk;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * describe:
 *
 * @author panwm
 * @since 2024/8/14 15:48
 */
@Data
public class ChunkTarget {

    @ApiModelProperty(value = "分片序号")
    private Integer partNumber;

    /**
     * 唯一id
     */
    @ApiModelProperty(value = "etag")
    private String etag;
}
