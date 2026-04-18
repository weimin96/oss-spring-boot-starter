package com.wiblog.oss.service;

import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;
import com.wiblog.oss.exception.OssException;
import com.wiblog.oss.support.AbstractServiceDynamicPropertyTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Operations 基类测试。
 *
 * <p>这里通过动态属性拿到真实配置，再用最小匿名子类验证基类约束，避免测试与固定本地配置耦合。</p>
 */
@DisplayName("Operations 基类")
class OperationsTest extends AbstractServiceDynamicPropertyTest {

    private Operations buildOperations() {
        return new Operations(ossProperties.toOptions(), mock(S3AsyncClient.class), mock(S3TransferManager.class)) {
        };
    }

    @Nested
    @DisplayName("域名前缀")
    class DomainTest {

        @Test
        @DisplayName("MinIO 动态属性应生成 Path-Style 域名前缀")
        void buildPathStyleDomain() {
            Operations operations = buildOperations();

            assertThat(operations.getDomain())
                    .isEqualTo(ossProperties.getEndpoint() + "/" + ossProperties.getBucketName() + "/");
        }

        @Test
        @DisplayName("真实上传后的 ObjectInfo URL 应包含动态域名前缀")
        void objectInfoUsesDynamicDomain() {
            String directory = newTestDirectory();
            String objectKey = putTextObject(directory, "domain.txt", "domain");

            ObjectInfo objectInfo = ossTemplate.query().getObjectInfo(objectKey);

            assertThat(objectInfo).isNotNull();
            assertThat(objectInfo.getUrl())
                    .isEqualTo(ossProperties.getEndpoint() + "/" + ossProperties.getBucketName() + "/" + objectKey);
        }
    }

    @Nested
    @DisplayName("请求处理")
    class HandleRequestTest {

        @Test
        @DisplayName("正常完成时应返回结果")
        void successReturnsValue() {
            Operations operations = buildOperations();

            String result = operations.handleRequest(() -> CompletableFuture.completedFuture("ok"));

            assertThat(result).isEqualTo("ok");
        }

        @Test
        @DisplayName("未知异常时应转为 OssException")
        void unknownExceptionThrowsOssException() {
            Operations operations = buildOperations();
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("network timeout"));

            assertThatThrownBy(() -> operations.handleRequest(() -> future))
                    .isInstanceOf(OssException.class)
                    .hasMessageContaining("Unexpected OSS error");
        }

        @Test
        @DisplayName("S3 异常缺少错误详情时不应触发空指针")
        void s3ExceptionWithoutDetailsDoesNotThrowNullPointer() {
            Operations operations = buildOperations();
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(software.amazon.awssdk.services.s3.model.S3Exception.builder()
                    .message("missing md5")
                    .build());

            assertThat(operations.handleRequest(() -> future)).isNull();
        }

        @Test
        @DisplayName("中断异常时应恢复线程中断标记")
        void interruptedExceptionSetsFlag() throws Exception {
            Operations operations = buildOperations();
            @SuppressWarnings("unchecked")
            CompletableFuture<String> future = mock(CompletableFuture.class);
            when(future.get()).thenThrow(new InterruptedException("interrupted"));

            assertThatThrownBy(() -> operations.handleRequest(() -> future))
                    .isInstanceOf(OssException.class)
                    .hasMessageContaining("interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            Thread.interrupted();
        }
    }

    @Nested
    @DisplayName("树形结构")
    class TreeTest {

        @Test
        @DisplayName("真实对象列表应构建为树形节点")
        void buildTreeFromRealObjects() {
            String directory = newTestDirectory();
            putTextObject(directory, "a.txt", "a");
            putTextObject(directory + "/nested", "b.txt", "b");

            ObjectTreeNode root = ossTemplate.query().getTreeList(directory);

            assertThat(root.getName()).isEqualTo(directory.substring(directory.lastIndexOf('/') + 1));
            assertThat(root.getChildren()).isNotEmpty();
        }
    }
}
