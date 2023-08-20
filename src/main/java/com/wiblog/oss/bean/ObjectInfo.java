package com.wiblog.oss.bean;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author panwm
 * @date 2023/8/20 17:09
 */
@Builder
@Data
public class ObjectInfo {

    private String uri;

    private String url;

    private String filename;

    private String uploadTime;
}
