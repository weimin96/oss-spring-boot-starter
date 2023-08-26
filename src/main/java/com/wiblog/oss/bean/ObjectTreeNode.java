package com.wiblog.oss.bean;

import com.amazonaws.services.s3.model.ObjectMetadata;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 文件对象树形结构
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

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date uploadTime;

    /**
     * 文件类型 folder/file
     */
    private String type;

    private List<ObjectTreeNode> children;

    public ObjectTreeNode(String name, String uri, String url, Date uploadTime, String type) {
        this.name = name;
        this.uri = uri;
        this.url = url;
        this.uploadTime = uploadTime;
        this.type = type;
        this.children = new ArrayList<>();
    }

    public List<ObjectTreeNode> getChildren() {
        return children;
    }

    public void addChild(ObjectTreeNode child) {
        children.add(child);
    }
}
