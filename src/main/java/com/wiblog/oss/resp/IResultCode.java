package com.wiblog.oss.resp;

import java.io.Serializable;

/**
 * describe:
 *
 * @author panwm
 * @since 2023/8/24 16:06
 */
public interface IResultCode extends Serializable {

    String getMessage();

    int getCode();
}
