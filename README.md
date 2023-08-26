# oss-spring-starter

通用对象存储工具

支持：
- MinIo
- 阿里云OSS
- 华为云OBS
- 腾讯云OSS

...

<!-- START doctoc -->
<!-- END doctoc -->

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
<!-- readme: collaborators,contributors -end -->
