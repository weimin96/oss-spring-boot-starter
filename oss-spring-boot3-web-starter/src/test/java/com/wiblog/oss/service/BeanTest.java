package com.wiblog.oss.service;

import com.wiblog.oss.bean.LazyDataList;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;
import com.wiblog.oss.support.AbstractServiceDynamicPropertyTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean 类测试。
 *
 * <p>该类虽然主要验证纯数据对象，但仍复用动态属性测试基类，保持 `service` 目录下测试的接入方式一致。</p>
 */
@DisplayName("Bean 类")
class BeanTest extends AbstractServiceDynamicPropertyTest {

    @Nested
    @DisplayName("ObjectInfo")
    class ObjectInfoTest {

        @Test
        @DisplayName("Builder 应正确写入所有字段")
        void builderSetsAllFields() {
            Date now = new Date();
            ObjectInfo info = ObjectInfo.builder()
                    .name("file.txt")
                    .uri("upload/file.txt")
                    .url("http://host/bucket/upload/file.txt")
                    .type("file")
                    .size(1024L)
                    .ext("txt")
                    .uploadTime(now)
                    .build();

            assertThat(info.getName()).isEqualTo("file.txt");
            assertThat(info.getUri()).isEqualTo("upload/file.txt");
            assertThat(info.getUrl()).isEqualTo("http://host/bucket/upload/file.txt");
            assertThat(info.getType()).isEqualTo("file");
            assertThat(info.getSize()).isEqualTo(1024L);
            assertThat(info.getExt()).isEqualTo("txt");
            assertThat(info.getUploadTime()).isEqualTo(now);
        }

        @Test
        @DisplayName("链式 setter 应保持可用")
        void chainedSetters() {
            ObjectInfo info = new ObjectInfo()
                    .setName("a.jpg")
                    .setType("file")
                    .setSize(500L);

            assertThat(info.getName()).isEqualTo("a.jpg");
            assertThat(info.getType()).isEqualTo("file");
            assertThat(info.getSize()).isEqualTo(500L);
        }
    }

    @Nested
    @DisplayName("ObjectTreeNode")
    class ObjectTreeNodeTest {

        @Test
        @DisplayName("addChild 应延迟初始化 children")
        void addChildLazyInitializes() {
            ObjectTreeNode root = new ObjectTreeNode("root", "root", "url", null, "folder", 0, null);
            ObjectTreeNode child = new ObjectTreeNode("child", "root/child", "url/child", null, "folder", 0, null);

            assertThat(root.getChildren()).isNull();
            root.addChild(child);

            assertThat(root.getChildren()).hasSize(1);
            assertThat(root.getChildren().get(0).getName()).isEqualTo("child");
        }

        @Test
        @DisplayName("多次 addChild 应持续追加")
        void multipleChildrenAccumulate() {
            ObjectTreeNode root = new ObjectTreeNode("r", "r", "u", null, "folder", 0, null);
            for (int i = 0; i < 5; i++) {
                root.addChild(new ObjectTreeNode("c" + i, "r/c" + i, "u/c" + i, null, "file", i, "txt"));
            }

            assertThat(root.getChildren()).hasSize(5);
        }
    }

    @Nested
    @DisplayName("LazyDataList")
    class LazyDataListTest {

        @Test
        @DisplayName("首次 addAll 时应初始化 records")
        void firstAddAllLazyInits() {
            LazyDataList<String> list = new LazyDataList<>();

            assertThat(list.getRecords()).isNull();
            list.addAll(List.of("a", "b"));

            assertThat(list.getRecords()).containsExactly("a", "b");
        }

        @Test
        @DisplayName("多次 addAll 应追加而非覆盖")
        void multipleAddAllAppends() {
            LazyDataList<Integer> list = new LazyDataList<>();
            list.addAll(List.of(1, 2));
            list.addAll(List.of(3, 4));

            assertThat(list.getRecords()).containsExactly(1, 2, 3, 4);
        }
    }
}
