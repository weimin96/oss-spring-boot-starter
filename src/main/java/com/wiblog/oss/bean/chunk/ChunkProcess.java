package com.wiblog.oss.bean.chunk;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 分片进度
 * @author panwm
 * @since 2023/8/20 23:11
 */
@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class ChunkProcess {

    /**
     * s3 对应的分片上传任务的id
     */
    private String uploadId;

    private String objectKey;

    /**
     * 分片信息
     */
    private List<ChunkPart> chunkList = new ArrayList<>();


    /**
     * 使用一个类来表示每个分片保存的信息
     * 对于本地文件系统 保存每块分片保存的路径
     * 对于AWS S3记录每个分片的eTag信息
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ChunkPart {

        private String location;

        private int chunkNumber;
    }
}
