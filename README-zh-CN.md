# OSS Spring Starter

[![Java CI](https://github.com/weimin96/oss-spring-boot-starter/actions/workflows/ci.yml/badge.svg)](https://github.com/weimin96/oss-spring-boot-starter/actions/workflows/ci.yml)
[![Coverage Status](https://coveralls.io/repos/github/weimin96/oss-spring-boot-starter/badge.svg?branch=main)](https://coveralls.io/github/weimin96/oss-spring-boot-starter?branch=main)
[![Maven central](https://maven-badges.herokuapp.com/maven-central/io.github.weimin96/oss-spring-boot-starter/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.weimin96/oss-spring-boot-starter)
[![License](https://img.shields.io/:license-apache-brightgreen.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![Last Commit](https://img.shields.io/github/last-commit/weimin96/oss-spring-boot-starter.svg)](https://github.com/weimin96/oss-spring-boot-starter)


README: [English](README.md) | [中文](README-zh-CN.md)

Wiki: [Wiki](https://github.com/weimin96/oss-spring-boot-starter/wiki)

## 简介

该项目主要是利用主流的OSS对象存储服务（`腾讯云OSS`/`阿里云OSS`/`华为云OBS`/`七牛云`/`MinIo`）都兼容Amazon S3（Simple Storage Service）协议的特性，基于Spring Boot的自动化配置特性提供一系列对象存储操作。

## 特性

- 支持：腾讯云、阿里云、华为云、七牛云、京东云、MinIo
- 提供一系列的基础 web 端点和 swagger 文档，支持自由开启
- - 提供大文件分片上传
- - 提供文件预览
- - ......
- 支持跨服务传输文件 `ossTemplate.put().transferObject()`
- 提供存储桶跨域配置 `oss.cross=true`

## 版本基础

- JDK 1.8
- Spring Boot 2.x

## 如何使用

### 引入依赖

- 使用 **Maven** 添加依赖项：

```xml
<dependency>
    <groupId>io.github.weimin96</groupId>
    <artifactId>oss-spring-boot-starter</artifactId>
    <version>${lastVersion}</version>
</dependency>
```

- 或者使用 **Gradle** 添加依赖项：
```gradle
dependencies {
  implementation 'io.github.weimin96:oss-spring-boot-starter:${lastVersion}'
}
```

- 在 `application.yml` 添加配置
```yaml
oss:
  endpoint: https://xxx.com
  access-key: YOUR_ACCESS_KEY
  secret-key: YOUR_SECRET_KEY
  bucket-name: your-bucket-name
  enable: true
```

- 代码使用
```java
@Autowired
private OssTemplate template;

// 上传文件
ossTemplate.put().putObject("bucket", "1.jpg", new File("/data/1.jpg"));
```

## 参数配置

所有的的配置说明

| Configuration        | Type     | Default Value | Description                  |
|----------------------|----------|---------------|------------------------------|
| oss.enable           | boolean  | false         | Enable OSS or not             |
| oss.endpoint         | String   |               | Endpoint of the OSS service   |
| oss.bucket-name      | String   |               | Bucket name                   |
| oss.access-key       | String   |               | Access key ID                 |
| oss.secret-key       | String   |               | Access secret key             |
| oss.cross            | boolean  | false         | Allow cross-origin or not      |
| oss.type             | String   |               | OSS type (options: obs/minio) |
| oss.max-connections       | int   |    50    | 最大连接数 默认50 |
| oss.connection-timeout        | int   |   10000     | 连接超时 默认10s |
| oss.http.prefix      | String   |               | Prefix of the endpoint URL    |
| oss.http.enable      | boolean  | false         | Enable web endpoints or not    |


