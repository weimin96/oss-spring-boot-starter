# oss-spring-starter

通用对象存储工具

支持：
- MinIo
- 阿里云OSS
- 华为云OBS
- 腾讯云OSS

...

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
**Table of Contents**

- [使用](#%E4%BD%BF%E7%94%A8)
- [使用方法](#%E4%BD%BF%E7%94%A8%E6%96%B9%E6%B3%95)
  - [配置文件](#%E9%85%8D%E7%BD%AE%E6%96%87%E4%BB%B6)
  - [代码使用](#%E4%BB%A3%E7%A0%81%E4%BD%BF%E7%94%A8)
- [Contributors](#contributors)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## 使用

- 引用
```xml
<dependency>
    <groupId>io.github.weimin96</groupId>
    <artifactId>oss-spring-starter</artifactId>
    <version>0.0.2</version>
</dependency>
```

## 使用方法

### 配置文件

```yaml
oss:
  endpoint: https://xxx.com
  access-key: YOUR_ACCESS_KEY
  secret-key: YOUR_SECRET_KEY
  bucket-name: your-bucket-name
```

### 代码使用

```java
@Autowired
private OssTemplate template;
```

## Contributors

<!-- readme: collaborators,contributors -start -->
<table>
<tr>
    <td align="center">
        <a href="https://github.com/weimin96">
            <img src="https://avatars.githubusercontent.com/u/20983152?v=4" width="100;" alt="weimin96"/>
            <br />
            <sub><b>Aoliao</b></sub>
        </a>
    </td></tr>
</table>
<!-- readme: collaborators,contributors -end -->
