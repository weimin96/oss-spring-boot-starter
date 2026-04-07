package com.wiblog.oss.service;

import com.wiblog.oss.bean.LazyDataList;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;
import com.wiblog.oss.support.AbstractServiceDynamicPropertyTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QueryOperations 集成测试。
 */
@DisplayName("QueryOperations")
class QueryOperationsTest extends AbstractServiceDynamicPropertyTest {

    @Test
    @DisplayName("连通性与对象存在性检查应返回真实结果")
    void connectivityAndExistence() {
        String directory = newTestDirectory();
        String objectKey = putTextObject(directory, "test.txt", "test!");

        assertThat(ossTemplate.query().testConnect()).isTrue();
        assertThat(ossTemplate.query().testConnectForBucket()).isTrue();
        assertThat(ossTemplate.query().checkExist(objectKey)).isTrue();
        assertThat(ossTemplate.query().checkExist(directory + "/missing.txt")).isFalse();
    }

    @Test
    @DisplayName("对象详情与文本内容应可读取")
    void objectInfoAndContent() {
        String directory = newTestDirectory();
        String objectKey = putTextObject(directory, "content.txt", "query-content");

        ObjectInfo objectInfo = ossTemplate.query().getObjectInfo(objectKey);

        assertThat(objectInfo).isNotNull();
        assertThat(objectInfo.getName()).isEqualTo("content.txt");
        assertThat(objectInfo.getUri()).isEqualTo(objectKey);
        assertThat(ossTemplate.query().getContent(objectKey)).isEqualTo("query-content");
    }

    @Test
    @DisplayName("列表、下一层目录与树形结构应反映真实对象")
    void listAndTree() {
        String directory = newTestDirectory();
        putTextObject(directory, "root.txt", "root");
        putTextObject(directory + "/nested", "child.txt", "child");

        List<ObjectInfo> objects = ossTemplate.query().listObjects(directory);
        List<ObjectTreeNode> nextLevel = ossTemplate.query().listNextLevel(directory);
        ObjectTreeNode tree = ossTemplate.query().getTreeList(directory);

        assertThat(objects).extracting(ObjectInfo::getUri)
                .contains(directory + "/root.txt", directory + "/nested/child.txt");
        assertThat(nextLevel).extracting(ObjectTreeNode::getType)
                .contains("file", "folder");
        assertThat(tree.getChildren()).isNotEmpty();
    }

    @Test
    @DisplayName("树查询无命中时不应返回占位目录节点")
    void emptyTreeShouldReturnNull() {
        String directory = newTestDirectory();

        ObjectTreeNode tree = ossTemplate.query().getTreeList(directory);
        ObjectTreeNode searchedTree = ossTemplate.query().getTreeListByName(directory, "missing");
        List<ObjectTreeNode> folderTree = ossTemplate.query().getFolderTreeList(directory);

        assertThat(tree).isNull();
        assertThat(searchedTree).isNull();
        assertThat(folderTree).isEmpty();
    }

    @Test
    @DisplayName("懒加载列表应支持第一页与续页查询")
    void lazyListSupportsPagination() {
        String directory = newTestDirectory();
        putTextObject(directory, "a.txt", "a");
        putTextObject(directory, "b.txt", "b");
        putTextObject(directory + "/sub", "c.txt", "c");

        LazyDataList<ObjectInfo> firstPage = ossTemplate.query().lazyList(directory, 1, null);
        LazyDataList<ObjectInfo> secondPage = ossTemplate.query().lazyList(directory, 1, firstPage.getContinuationToken());

        assertThat(firstPage.getRecords()).isNotEmpty();
        assertThat(firstPage.getMaxKeys()).isEqualTo(1);
        assertThat(secondPage.getRecords()).isNotNull();
    }
}
