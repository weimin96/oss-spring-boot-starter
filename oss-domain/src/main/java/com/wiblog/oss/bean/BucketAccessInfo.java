package com.wiblog.oss.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Bucket ACL 信息。
 * <p>
 * 这里对外只暴露 S3 默认 canned ACL 名称，
 * 目的是让前端和调用方围绕稳定的权限枚举交互，
 * 而不是直接耦合到底层 grants 明细结构。
 *
 * @author panwm
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class BucketAccessInfo {
    private String bucketName;
    private String acl;
    private boolean supported;
    private String message;
}


