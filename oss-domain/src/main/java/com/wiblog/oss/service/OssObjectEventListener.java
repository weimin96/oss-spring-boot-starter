package com.wiblog.oss.service;

import com.wiblog.oss.bean.OssObjectEvent;

/**
 * 对象变化事件监听器。
 *
 * @author panwm
 */
public interface OssObjectEventListener {

    /**
     * 处理对象变化事件。
     *
     * @param event 对象变化事件
     */
    void onObjectChanged(OssObjectEvent event);
}
