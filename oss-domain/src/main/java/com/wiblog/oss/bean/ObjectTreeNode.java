package com.wiblog.oss.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class ObjectTreeNode extends ObjectInfo {

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date uploadTime;

    /**
     * 之所以保留字符串而非枚举，是为了兼容现有返回协议，避免调用方联动调整。
     */
    private String type;
    private List<ObjectTreeNode> children;

    /**
     * 构造一个树节点。
     *
     * <p>这里继续使用显式构造参数而不是工厂枚举，是为了保持现有 JSON 协议与树构建流程稳定，
     * 避免目录树接口在升级过程中联动调整前端解析逻辑。</p>
     *
     * @param name       节点名称
     * @param uri        节点在对象存储中的完整路径
     * @param url        节点可访问地址
     * @param uploadTime 上传时间
     * @param type       节点类型，`folder` 表示目录，`file` 表示文件
     * @param size       文件大小；目录节点通常为 0
     * @param ext        文件扩展名；目录节点通常为 {@code null}
     */
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

    /**
     * 返回子节点列表。
     *
     * <p>当节点尚未展开任何子项时返回 {@code null}，
     * 这样可以和历史协议保持一致，不把“未初始化”与“显式空目录”混为一谈。</p>
     *
     * @return 子节点列表；未初始化时为 {@code null}
     */
    public List<ObjectTreeNode> getChildren() {
        return children;
    }

    /**
     * 追加一个子节点。
     *
     * <p>树构建过程采用按需初始化列表的方式，
     * 目的是在大量目录节点下减少无意义的空集合创建。</p>
     *
     * @param child 需要挂载到当前节点下的子节点
     */
    public void addChild(ObjectTreeNode child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        children.add(child);
    }
}


