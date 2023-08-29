package com.wiblog.oss;

import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.util.IOUtils;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;
import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.service.OssTemplate;
import lombok.SneakyThrows;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.ResourceUtils;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

@SpringBootApplication(scanBasePackages = "com.wiblog.oss")
@SpringBootTest
@ActiveProfiles("minio")
class OssSpringStarterApplicationTests {

    /**
     * 测试文件
     */
    private static final String TEST_FILE_NAME = "test.txt";

    /**
     * 测试目录
     */
    private static final String TEST_FOLDER_NAME = "data";

    @Autowired
    private OssTemplate ossTemplate;

    /**
     * 上传文件
     * @throws IOException IOException
     */
    @Test
    @BeforeEach
    void init() throws IOException {
        File file = ResourceUtils.getFile(ResourceUtils.CLASSPATH_URL_PREFIX + TEST_FILE_NAME);
        ossTemplate.put().putObject(TEST_FOLDER_NAME, TEST_FILE_NAME, file);
    }

    @AfterEach
    public void destroy() {
        ossTemplate.delete().removeObject(TEST_FOLDER_NAME + "/" + TEST_FILE_NAME);
        ObjectInfo objectInfo = ossTemplate.query().getObjectInfo(TEST_FOLDER_NAME + "/" + TEST_FILE_NAME);
        Assertions.assertNull(objectInfo);
    }

    /**
     * 测试获取文件信息
     */
    @Test
    void getObject() {
        ObjectInfo object = ossTemplate.query().getObjectInfo(TEST_FOLDER_NAME + "/" + TEST_FILE_NAME);
        Assertions.assertEquals(TEST_FILE_NAME, object.getName());
        Assertions.assertEquals(TEST_FOLDER_NAME + "/" + TEST_FILE_NAME, object.getUri());
    }

    /**
     * 测试获取文件信息
     */
    @Test
    void getInputStream() {
        String content = ossTemplate.query().getContent( TEST_FOLDER_NAME + "/" + TEST_FILE_NAME);
        Assertions.assertTrue(content.contains("test!"));
    }

    @Test
    void getTree() {
        ObjectTreeNode treeList = ossTemplate.query().getTreeList(TEST_FOLDER_NAME);
        Assertions.assertEquals(treeList.getName(), TEST_FOLDER_NAME);
        Assertions.assertEquals(treeList.getChildren().get(0).getName(), TEST_FILE_NAME);
    }

    @Test
    void getListObjectSummary() {
        List<S3ObjectSummary> s3ObjectSummaries = ossTemplate.query().listObjectSummary(TEST_FOLDER_NAME);
        Assertions.assertEquals(s3ObjectSummaries.get(0).getKey(), TEST_FOLDER_NAME + "/" + TEST_FILE_NAME);
    }

}
