package com.wiblog.oss;

import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;
import com.wiblog.oss.service.OssTemplate;
import com.wiblog.oss.util.Util;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.util.List;

@SpringBootApplication(scanBasePackages = "com.wiblog.oss")
@SpringBootTest
@ActiveProfiles("obs")
class OssSpringStarterApplicationTests {

    @Autowired
    private OssTemplate ossTemplate;

    @Test
    void contextLoads() throws FileNotFoundException {
        File file = new File("C:\\Users\\pwm\\Pictures\\711554e282e04aa89f2e34140c358bd7.jpg");
        InputStream is = new FileInputStream(file);
        ObjectInfo resp = ossTemplate.put().putObject("data/bucket", "1.jpg", is);
        System.out.println(resp.toString());
    }

    @Test
    void textGet() {
        ObjectInfo object = ossTemplate.query().getObject("data/bucket/1.jpg");
        System.out.println(object.toString());
    }

    @Test
    public void uploadDir() {
        File dir = new File("C:\\Users\\pwm\\Pictures\\Saved Pictures\\有道图片");
        ossTemplate.put().uploadDir("folder", dir);
    }

    @Test
    void textGet2() {
        ObjectTreeNode folder = ossTemplate.query().getTreeList("folder");
        System.out.println(folder);
    }

    @Test
    public void testUpload() throws Exception {
        String chunkFileFolder = "C:/Users/pwm/Downloads/data/bucket/";
        File file = new File("C:\\Users\\pwm\\Downloads\\flowable-6.8.0.zip");
        long contentLength = file.length();
        // 每块大小设置为20MB
        long partSize = 20 * 1024 * 1024;
        // 文件分片块数 最后一块大小可能小于 20MB
        long chunkFileNum = (long) Math.ceil(contentLength * 1.0 / partSize);
        RestTemplate restTemplate = new RestTemplate();

        try (RandomAccessFile raf_read = new RandomAccessFile(file, "r")) {
            // 缓冲区
            byte[] b = new byte[1024];
            for (int i = 1; i <= chunkFileNum; i++) {
                // 块文件
                File chunkFile = new File(chunkFileFolder + i);
                // 创建向块文件的写对象
                try (RandomAccessFile raf_write = new RandomAccessFile(chunkFile, "rw")) {
                    int len;
                    while ((len = raf_read.read(b)) != -1) {
                        raf_write.write(b, 0, len);
                        // 如果块文件的大小达到20M 开始写下一块儿  或者已经到了最后一块
                        if (chunkFile.length() >= partSize) {
                            break;
                        }
                    }
                    // 上传
                    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                    body.add("file", new FileSystemResource(chunkFile));
                    body.add("chunkNumber", i);
                    body.add("chunkSize", partSize);
                    body.add("currentChunkSize", chunkFile.length());
                    body.add("totalSize", contentLength);
                    body.add("filename", file.getName());
                    body.add("totalChunks", chunkFileNum);
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                    HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
                    String serverUrl = "http://localhost:8080/oss/chunk";
                    ResponseEntity<String> response = restTemplate.postForEntity(serverUrl, requestEntity, String.class);
                    System.out.println("Response code: " + response.getStatusCode() + " Response body: " + response.getBody());
                } finally {
                    Util.deleteFile(chunkFile.getPath());
                }
            }
        }
        // 合并文件
        String mergeUrl = "http://localhost:8080/oss/merge?filename=" + file.getName();
        ResponseEntity<String> response = restTemplate.getForEntity(mergeUrl, String.class);
        System.out.println("Response code: " + response.getStatusCode() + " Response body: " + response.getBody());
    }

}
