package com.wiblog.oss.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 文件对象树形结构
 *
 * @author panwm
 * @since 2023/8/22 0:05
 */
@Getter
@Setter
@Accessors(chain = true)
public class ObjectTreeNode extends ObjectInfo {

    private String name;

    private String uri;

    private String url;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date uploadTime;

    /**
     * 文件类型 folder/file
     */
    private String type;

    private long size;

    private String ext;

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
