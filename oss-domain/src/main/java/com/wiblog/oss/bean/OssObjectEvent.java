package com.wiblog.oss.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 对象变化事件。
 *
 * @author panwm
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class OssObjectEvent {

    /**
     * Bucket 名称需要随事件携带，避免多 Bucket 监听时依赖外部上下文。
     */
    private String bucketName;

    /**
     * 对象键是事件处理的主标识，上传、删除和覆盖事件都以它定位文件。
     */
    private String objectKey;

    /**
     * 原始事件名称保持 S3 语义，便于业务区分创建、删除和分片完成等事件。
     */
    private String eventName;

    private String eventTime;

    private Long size;

    private String eTag;

    private String versionId;

    private String sequencer;
}
