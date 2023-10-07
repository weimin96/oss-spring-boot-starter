package com.wiblog.oss;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.util.IOUtils;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;
import com.wiblog.oss.service.OssTemplate;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.annotation.BeforeTestMethod;
import org.springframework.util.ResourceUtils;

import java.io.*;
import java.nio.file.Files;
import java.util.List;

@SpringBootApplication(scanBasePackages = "com.wiblog.oss")
@SpringBootTest
@ActiveProfiles("minio")
class OssSpringStarterApplicationTests {

    /**
     * 测试文件
     */
    private static final String TEST_FILE_NAME = "test.txt";

    /**
     * 测试文件夹
     */
    private static final String TEST_FOLDER_NAME= "data";

    /**
     * 测试上传路径
     */
    private static final String TEST_UPLOAD_PATH = "upload";

    @Autowired
    private OssTemplate ossTemplate;

    /**
     * 上传文件
     * @throws IOException IOException
     */
    @BeforeEach
    void init() throws IOException {
        File file = ResourceUtils.getFile(ResourceUtils.CLASSPATH_URL_PREFIX + TEST_FOLDER_NAME + "/" + TEST_FILE_NAME);
        ossTemplate.put().putObject(TEST_UPLOAD_PATH, TEST_FILE_NAME, file);
    }

    @AfterEach
    public void destroy() {
        ossTemplate.delete().removeObject(TEST_UPLOAD_PATH + "/" + TEST_FILE_NAME);
        ObjectInfo objectInfo = ossTemplate.query().getObjectInfo(TEST_UPLOAD_PATH + "/" + TEST_FILE_NAME);
        Assertions.assertNull(objectInfo);
    }

    /**
     * 测试上传文件夹
     */
//    @Test
//    void testPutFolder() throws IOException {
//        File folder = ResourceUtils.getFile(ResourceUtils.CLASSPATH_URL_PREFIX + TEST_FOLDER_NAME);
//        ossTemplate.put().putFolder(TEST_UPLOAD_PATH, folder);
//
//        InputStream inputStream = ossTemplate.query().getInputStream(TEST_UPLOAD_PATH + "/" + TEST_FOLDER_NAME + "/" + TEST_FILE_NAME);
//        String content = IOUtils.toString(inputStream);
//        Assertions.assertTrue(content.contains("test!"));
//
//        ossTemplate.delete().removeFolder(TEST_UPLOAD_PATH);
//    }

    /**
     * 测试拷贝文件
     */
    @Test
    public void testCopyFile() throws IOException {
        String copyPath = "copy/" + TEST_FILE_NAME;
        ossTemplate.put().copyFile(TEST_UPLOAD_PATH + "/" + TEST_FILE_NAME, copyPath);

        File folder = ResourceUtils.getFile(ResourceUtils.CLASSPATH_URL_PREFIX + TEST_FOLDER_NAME);
        String path = folder.getParent() + File.separator + TEST_FILE_NAME;
        File file = ossTemplate.query().getFile(copyPath, path);
        String content = IOUtils.toString(Files.newInputStream(file.toPath()));
        Assertions.assertTrue(content.contains("test!"));

        ossTemplate.delete().removeObject(copyPath);
    }

    /**
     * 测试获取文件信息
     */
    @Test
    void getObject() {
        ObjectInfo object = ossTemplate.query().getObjectInfo(TEST_UPLOAD_PATH + "/" + TEST_FILE_NAME);
        Assertions.assertEquals(TEST_FILE_NAME, object.getName());
        Assertions.assertEquals(TEST_UPLOAD_PATH + "/" + TEST_FILE_NAME, object.getUri());
    }

    /**
     * 测试获取文件信息
     */
    @Test
    void getInputStream() {
        String content = ossTemplate.query().getContent( TEST_UPLOAD_PATH + "/" + TEST_FILE_NAME);
        Assertions.assertTrue(content.contains("test!"));
    }

    /**
     * 测试获取目录树
     */
    @Test
    void getTree() {
        ObjectTreeNode treeList = ossTemplate.query().getTreeList(TEST_UPLOAD_PATH);
        Assertions.assertEquals(treeList.getName(), TEST_UPLOAD_PATH);
        Assertions.assertEquals(treeList.getChildren().get(0).getName(), TEST_FILE_NAME);
    }

    /**
     * 测试获取列表
     */
    @Test
    void getListObjectSummary() {
        List<S3ObjectSummary> s3ObjectSummaries = ossTemplate.query().listObjectSummary(TEST_UPLOAD_PATH);
        Assertions.assertEquals(s3ObjectSummaries.get(0).getKey(), TEST_UPLOAD_PATH + "/" + TEST_FILE_NAME);
    }

    /**
     * 测试获取文件夹
     */
    @Test
    void getFolder() throws IOException {
        File folder = ResourceUtils.getFile(ResourceUtils.CLASSPATH_URL_PREFIX + TEST_FOLDER_NAME);
        ossTemplate.put().putFolder(TEST_UPLOAD_PATH, folder);

        String path = folder.getParent() + File.separator + "download";

        ossTemplate.query().getFolder(TEST_UPLOAD_PATH, path);

        File file = new File(path + File.separator + TEST_FOLDER_NAME + File.separator + TEST_FILE_NAME);

        String content = IOUtils.toString(Files.newInputStream(file.toPath()));
        Assertions.assertTrue(content.contains("test!"));

        ossTemplate.delete().removeFolder(TEST_UPLOAD_PATH);
    }

}
