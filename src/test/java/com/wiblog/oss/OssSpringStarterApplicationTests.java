//package com.wiblog.oss;
//
//import com.wiblog.oss.bean.ObjectInfo;
//import com.wiblog.oss.bean.ObjectTreeNode;
//import com.wiblog.oss.bean.OssProperties;
//import com.wiblog.oss.service.OssTemplate;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.ActiveProfiles;
//
//import java.io.File;
//import java.io.FileNotFoundException;
//import java.io.IOException;
//import java.io.InputStream;
//import java.nio.file.Files;
//
//@SpringBootApplication(scanBasePackages = "com.wiblog.oss")
//@SpringBootTest
//@ActiveProfiles("obs")
////@ActiveProfiles("minio")
//class OssSpringStarterApplicationTests {
//
//    @Autowired
//    private OssTemplate ossTemplate;
//
//    /**
//     * 测试上传文件
//     * @throws FileNotFoundException FileNotFoundException
//     */
//    @Test
//    void contextLoads() throws IOException {
//        File file = new File("C:\\711554e282e04aa89f2e34140c358bd7.jpg");
//        InputStream is = Files.newInputStream(file.toPath());
//        ObjectInfo resp = ossTemplate.put().putObject("data/bucket", "1.jpg", is);
//        System.out.println(resp.toString());
//    }
//
//    /**
//     * 测试获取文件信息
//     */
//    @Test
//    void testGetObjectInfo() {
//        ObjectInfo object = ossTemplate.query().getObjectInfo("sys-plat/bg-2.png");
//        System.out.println(object.toString());
//    }
//
//    /**
//     * 测试上传文件夹
//     */
//    @Test
//    public void uploadDir() {
//        File dir = new File("C:\\Pictures");
//        ossTemplate.put().putFolder("folder", dir);
//    }
//
//    /**
//     * 测试查询树形列表
//     */
//    @Test
//    void testTreeList() {
//        ObjectTreeNode folder = ossTemplate.query().getTreeList("folder");
//    }
//
//    /**
//     * 测试从minio传输文件到系统存储
//     */
//    @Test
//    void testTransferObject() {
//        OssProperties properties = new OssProperties();
//        properties.setEndpoint("http://10.3.1.136:9000");
//        properties.setAccessKey("minioadmin");
//        properties.setSecretKey("minioadmin");
//        OssTemplate source = new OssTemplate(properties);
//
//        ossTemplate.put().transferObject(source, "guodi", "sys-plat", "data");
//    }
//
//}
