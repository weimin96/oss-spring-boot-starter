package com.wiblog.oss.bean;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Bucket ACL 信息")
public class BucketAccessInfo {

    @Schema(description = "Bucket 名称")
    private String bucketName;

    @Schema(description = "S3 默认 ACL 名称，如 private、public-read")
    private String acl;

    @Schema(description = "当前存储是否支持 Bucket ACL 能力")
    private boolean supported;

    @Schema(description = "能力说明或失败提示")
    private String message;
}
