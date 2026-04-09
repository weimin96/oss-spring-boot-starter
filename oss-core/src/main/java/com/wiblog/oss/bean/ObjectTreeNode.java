package com.wiblog.oss.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 对象存储目录树节点。
 *
 * @author panwm
 * @since 2023/8/22 0:05
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "对象存储目录树节点")
public class ObjectTreeNode extends ObjectInfo {

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "节点上传时间")
    private Date uploadTime;

    /**
     * 之所以保留字符串而非枚举，是为了兼容现有返回协议，避免调用方联动调整。
     */
    @Schema(description = "节点类型，folder 表示目录，file 表示文件")
    private String type;

    @Schema(description = "子节点列表")
    private List<ObjectTreeNode> children;

    public ObjectTreeNode(String name, String uri, String url, Date uploadTime, String type, long size, String ext) {
        this.name = name;
        this.uri = uri;
        this.url = url;
        this.uploadTime = uploadTime;
        this.type = type;
        this.children = null;
        this.size = size;
        this.ext = ext;
    }

    public List<ObjectTreeNode> getChildren() {
        return children;
    }

    public void addChild(ObjectTreeNode child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        children.add(child);
    }
}
