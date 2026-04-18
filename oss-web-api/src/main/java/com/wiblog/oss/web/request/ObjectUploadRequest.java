package com.wiblog.oss.web.request;

import com.wiblog.oss.web.validation.OssWebRequestValidator;
import com.wiblog.oss.web.file.OssUploadFile;
import lombok.Data;

import java.io.IOException;
import java.io.InputStream;

/**
 * 对象上传请求。
 *
 * <p>该请求对象把文件上传所需的 HTTP 字段收拢到共享契约层，
 * 让三个 Web Starter 只保留适配逻辑，不再各自维护同样的参数展开代码。</p>
 *
 * @author panwm
 */
@Data
public class ObjectUploadRequest {
    private OssUploadFile file;
    private String path;
    private String filename;

    /**
     * 校验上传请求必填项。
     */
    public void validate() {
        OssWebRequestValidator.requireFile(file, "file");
        OssWebRequestValidator.requireText(path, "path");
    }

    /**
     * 打开上传内容流。
     *
     * @return 文件输入流
     * @throws IOException 读取失败时抛出
     */
    public InputStream openStream() throws IOException {
        return file.openStream();
    }

    /**
     * 解析最终文件名。
     *
     * @return 优先使用显式文件名，否则回退到原始上传文件名
     */
    public String resolveFilename() {
        if (filename != null && !filename.trim().isEmpty()) {
            return filename;
        }
        return file == null ? null : file.getOriginalFilename();
    }
}
