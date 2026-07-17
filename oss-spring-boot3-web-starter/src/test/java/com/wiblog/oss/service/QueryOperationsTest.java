package com.wiblog.oss.service;

import com.wiblog.oss.bean.LazyDataList;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;
import com.wiblog.oss.bean.ReadObjectRangeCommand;
import com.wiblog.oss.support.AbstractServiceDynamicPropertyTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

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
    @DisplayName("类型化区间读取应使用默认 Bucket 并返回指定字节")
    void typedRangeReadUsesDefaultBucket() throws Exception {
        String directory = newTestDirectory();
        String objectKey = putTextObject(directory, "range.txt", "0123456789");

        try (InputStream inputStream = ossTemplate.query().getInputStream(
                new ReadObjectRangeCommand(null, objectKey, 2L, 4L))) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("2345");
        }
    }

    @Test
    @DisplayName("列表、下一层目录与树形结构应反映真实对象")
    void listAndTree() {
        String directory = newTestDirectory();
        String directoryWithTrailingSlash = directory + "/";
        putTextObject(directory, "root.txt", "root");
        putTextObject(directory + "/nested", "child.txt", "child");

        List<ObjectInfo> objects = ossTemplate.query().listObjects(directory);
        List<ObjectTreeNode> nextLevel = ossTemplate.query().listNextLevel(directory);
        List<ObjectTreeNode> folderTree = ossTemplate.query().getFolderTreeList(directoryWithTrailingSlash);
        ObjectTreeNode tree = ossTemplate.query().getTreeList(directoryWithTrailingSlash);

        assertThat(objects).extracting(ObjectInfo::getUri)
                .contains(directory + "/root.txt", directory + "/nested/child.txt");
        assertThat(nextLevel).extracting(ObjectTreeNode::getType)
                .contains("file", "folder");
        assertThat(folderTree).singleElement().satisfies(folder -> {
            assertThat(folder.getName()).isEqualTo("nested");
            assertThat(folder.getType()).isEqualTo("folder");
            assertThat(folder.getUri()).isEqualTo(directory + "/nested");
            assertThat(folder.getUrl()).endsWith(directory + "/nested");
            assertThat(folder.getUri()).doesNotContain("//");
            assertThat(folder.getUrl()).doesNotContain(directory + "//");
        });
        assertThat(tree.getUri()).isEqualTo(directory);
        assertThat(tree.getUrl()).endsWith(directory);
        assertThat(tree.getChildren()).hasSize(2);
        assertThat(tree.getChildren()).filteredOn(node -> "folder".equals(node.getType()))
                .singleElement()
                .satisfies(folder -> {
                    ObjectTreeNode folderNode = (ObjectTreeNode) folder;
                    assertThat(folderNode.getName()).isEqualTo("nested");
                    assertThat(folderNode.getUri()).isEqualTo(directory + "/nested");
                    assertThat(folderNode.getUrl()).endsWith(directory + "/nested");
                    assertThat(folderNode.getChildren()).singleElement().satisfies(file -> {
                        assertThat(file.getType()).isEqualTo("file");
                        assertThat(file.getUri()).isEqualTo(directory + "/nested/child.txt");
                        assertThat(file.getUrl()).endsWith(directory + "/nested/child.txt");
                    });
                });
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
        assertThat(firstPage.getRecords()).extracting(ObjectInfo::getType)
                .contains("folder", "file");
        assertThat(firstPage.getRecords().stream()
                .filter(item -> "folder".equals(item.getType()))
                .map(ObjectInfo::getUri)
                .collect(Collectors.toList()))
                .contains(directory + "/sub");
        assertThat(firstPage.getMaxKeys()).isEqualTo(1);
        assertThat(secondPage.getRecords()).isNotNull();
    }
}
