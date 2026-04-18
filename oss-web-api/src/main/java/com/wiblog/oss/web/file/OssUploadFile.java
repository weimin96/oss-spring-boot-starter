package com.wiblog.oss.web.file;

import java.io.IOException;
import java.io.InputStream;

/**
 * OSS 上传文件访问契约。
 *
 * <p>共享 Web 契约层不能依赖 Spring MultipartFile，
 * 因为该模块需要同时服务 Boot 2/3/4，且不应把具体 Web 框架类型传递给下游。
 * 这里仅保留上传业务真正需要的文件能力，由各 Web Starter 提供框架适配。</p>
 *
 * @author panwm
 */
public interface OssUploadFile {

    /**
     * 判断上传内容是否为空。
     *
     * @return 内容为空时返回 true
     */
    boolean isEmpty();

    /**
     * 获取客户端提交的原始文件名。
     *
     * @return 原始文件名
     */
    String getOriginalFilename();

    /**
     * 打开上传内容流。
     *
     * @return 上传内容输入流
     * @throws IOException 文件读取失败时抛出
     */
    InputStream openStream() throws IOException;

    /**
     * 读取完整上传内容。
     *
     * @return 上传内容字节
     * @throws IOException 文件读取失败时抛出
     */
    byte[] readBytes() throws IOException;

    /**
     * 获取上传内容长度。
     *
     * @return 内容长度
     */
    long getSize();
}
