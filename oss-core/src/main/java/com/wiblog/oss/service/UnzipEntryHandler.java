package com.wiblog.oss.service;

import java.io.InputStream;
import java.util.zip.ZipEntry;

/**
 * ZIP 条目处理器，供调用方自定义解压逻辑。
 *
 * <p>每解压出一个 ZIP 条目，框架将调用此接口一次。调用方可在 handle 方法内：
 * <ul>
 *   <li>直接写入响应流（HTTP 场景）</li>
 *   <li>上传到另一个 OSS 路径</li>
 *   <li>写入本地文件系统</li>
 *   <li>忽略（直接返回）</li>
 * </ul>
 * <b>注意：</b>不要关闭传入的 InputStream，框架会负责关闭。
 *
 * @author panwm
 */
@FunctionalInterface
public interface UnzipEntryHandler {

    /**
     * 处理单个 ZIP 条目。
     *
     * @param entry  ZIP 条目元数据（文件名、大小等）
     * @param stream 条目内容流；调用方只能读，不能关闭
     * @throws Exception 处理异常时抛出，框架将记录并计入失败列表
     */
    void handle(ZipEntry entry, InputStream stream) throws Exception;
}