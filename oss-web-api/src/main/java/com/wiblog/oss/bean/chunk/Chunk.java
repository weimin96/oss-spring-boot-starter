package com.wiblog.oss.bean.chunk;

import com.wiblog.oss.exception.OssException;
import com.wiblog.oss.web.file.OssUploadFile;
import lombok.Data;

/**
 * 文件分片上传请求。
 *
 * <p>文件内容通过 {@link OssUploadFile} 表达，
 * 是为了让共享 Web 契约只描述上传能力，不绑定任何具体 Web 框架。</p>
 *
 * @author panwm
 * @since 2023/8/20 23:10
 */
@Data
public class Chunk {
    private Integer chunkNumber;
    private String filename;
    private String path;
    private String guid;

    private OssUploadFile file;
    private String uploadId;

    /**
     * 转换为核心实现可复用的分片上传命令。
     *
     * @return 领域层上传命令
     */
    public ChunkUploadCommand toCommand() {
        if (file == null || file.isEmpty()) {
            throw new OssException("INVALID_REQUEST", "分片文件不能为空");
        }
        try {
            ChunkUploadCommand command = new ChunkUploadCommand();
            command.setChunkNumber(chunkNumber);
            command.setFilename(filename);
            command.setPath(path);
            command.setGuid(guid);
            command.setUploadId(uploadId);
            command.setContentLength(file.getSize());
            command.setFileBytes(file.readBytes());
            return command;
        } catch (Exception exception) {
            throw new OssException("INVALID_REQUEST", "读取分片文件失败", exception);
        }
    }
}


