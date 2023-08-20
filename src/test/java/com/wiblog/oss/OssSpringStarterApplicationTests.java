package com.wiblog.oss;

import com.wiblog.oss.bean.ObjectResp;
import com.wiblog.oss.service.OssTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

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
        ObjectResp resp = ossTemplate.put().putObject("1", "1.jpg", is);
        System.out.println(resp.toString());
    }

    @Test
    public void uploadDir() {
        File dir = new File("C:\\Users\\pwm\\Pictures\\Saved Pictures\\有道图片");
        ossTemplate.put().uploadDir("folder", dir);
    }

}
