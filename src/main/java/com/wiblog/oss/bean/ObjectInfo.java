package com.wiblog.oss.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * @author panwm
 * @since  2023/8/20 17:09
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class ObjectInfo {

    protected String name;

    protected String uri;

    protected String url;

    protected long size;

    protected String ext;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    protected Date uploadTime;
}
