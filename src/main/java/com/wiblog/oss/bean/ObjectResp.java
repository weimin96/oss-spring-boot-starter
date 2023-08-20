package com.wiblog.oss.bean;

import lombok.Builder;
import lombok.Data;

/**
 * @author panwm
 * @date 2023/8/20 17:09
 */
@Builder
@Data
public class ObjectResp {

    private String uri;

    private String url;

    private String filename;
}
