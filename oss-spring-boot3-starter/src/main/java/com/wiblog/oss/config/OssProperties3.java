package com.wiblog.oss.config;

import com.wiblog.oss.bean.OssProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Spring Boot 3.x OSS 配置属性（jakarta.validation）。
 *
 * @author panwm
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Validated
@ConfigurationProperties(prefix = OssProperties.PREFIX)
@Schema(description = "OSS 客户端配置（Spring Boot 3）")
public class OssProperties3 extends OssProperties {

    @NotBlank(message = "oss.endpoint 不能为空")
    @Override
    public String getEndpoint() { return super.getEndpoint(); }

    @NotBlank(message = "oss.access-key 不能为空")
    @Override
    public String getAccessKey() { return super.getAccessKey(); }

    @NotBlank(message = "oss.secret-key 不能为空")
    @Override
    public String getSecretKey() { return super.getSecretKey(); }

    @Min(value = 1, message = "oss.max-connections 最小为 1")
    @Override
    public int getMaxConnections() { return super.getMaxConnections(); }

    @Min(value = 0, message = "oss.connection-timeout 不能为负数")
    @Override
    public long getConnectionTimeout() { return super.getConnectionTimeout(); }

    @Min(value = 5, message = "oss.part-size-in-mb 最小为 5MB")
    @Override
    public int getPartSizeInMb() { return super.getPartSizeInMb(); }
}
