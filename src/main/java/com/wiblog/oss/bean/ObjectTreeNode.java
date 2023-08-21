package com.wiblog.oss.bean;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件对象树形结构
 * @author panwm
 * @date 2023/8/22 0:05
 */
public class ObjectTreeNode extends ObjectInfo {

    private List<ObjectTreeNode> children;

    public ObjectTreeNode(String name) {
        this.setName(name);
        this.children = new ArrayList<>();
    }

    public List<ObjectTreeNode> getChildren() {
        return children;
    }

    public void addChild(ObjectTreeNode child) {
        children.add(child);
    }
}
