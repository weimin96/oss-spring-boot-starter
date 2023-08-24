package com.wiblog.oss.constant;

/**
 * describe:
 *
 * @author panwm
 * @since 2023/8/24 16:06
 */
public enum ClientEnum {

    OBS("obs", "obs客户端"),
    MINIO("minio", "minio客户端");

    final String name;
    final String type;

    ClientEnum(String type, String name) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }
}
