package com.wiblog.oss.bean;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件块
 * @author panwm
 * @date 2023/8/20 23:10
 */
@Data
public class Chunk {

    /**
     * 当前文件块，从1开始
     */
    private Integer chunkNumber;

    /**
     * 分块大小
     */
    private Long chunkSize;

    /**
     * 当前分块大小
     */
    private Long currentChunkSize;

    /**
     * 总大小
     */
    private Long totalSize;

    /**
     * 文件名
     */
    private String filename;

    /**
     * 总块数
     */
    private Integer totalChunks;

    /**
     * 分块文件内容
     */
    private MultipartFile file;
}
