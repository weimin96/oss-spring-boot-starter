package com.wiblog.oss.web.adapter;

import com.wiblog.oss.web.file.OssUploadFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Spring MultipartFile 上传文件适配器。
 *
 * <p>Spring 类型只允许存在于 Web Starter 内部，
 * 通过该适配器转换为共享契约后，控制器之外的请求模型就不再感知具体框架。</p>
 *
 * @author panwm
 */
public class SpringMultipartUploadFile implements OssUploadFile {

    private final MultipartFile multipartFile;

    public SpringMultipartUploadFile(MultipartFile multipartFile) {
        this.multipartFile = multipartFile;
    }

    @Override
    public boolean isEmpty() {
        return multipartFile == null || multipartFile.isEmpty();
    }

    @Override
    public String getOriginalFilename() {
        return multipartFile == null ? null : multipartFile.getOriginalFilename();
    }

    @Override
    public InputStream openStream() throws IOException {
        return multipartFile.getInputStream();
    }

    @Override
    public byte[] readBytes() throws IOException {
        return multipartFile.getBytes();
    }

    @Override
    public long getSize() {
        return multipartFile == null ? 0 : multipartFile.getSize();
    }
}
