# OSS Spring Starter

[![Java CI](https://github.com/weimin96/oss-spring-starter/actions/workflows/coveralls.yml/badge.svg)](https://github.com/weimin96/oss-spring-starter/actions/workflows/coveralls.yml)
[![Coverage Status](https://coveralls.io/repos/github/weimin96/oss-spring-starter/badge.svg?branch=main)](https://coveralls.io/github/weimin96/oss-spring-starter?branch=main)
[![Maven central](https://maven-badges.herokuapp.com/maven-central/io.github.weimin96/oss-spring-starter/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.weimin96/oss-spring-starter)
[![License](https://img.shields.io/:license-apache-brightgreen.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![Last Commit](https://img.shields.io/github/last-commit/weimin96/oss-spring-starter.svg)](https://github.com/weimin96/oss-spring-starter)




README: [English](README.md) | [中文](README-zh-CN.md)

Wiki: [Wiki](https://github.com/weimin96/oss-spring-starter/wiki)

## Introduction

This project mainly utilizes the mainstream OSS object storage services (`Tencent Cloud OSS` / `Alibaba Cloud OSS` / `Huawei Cloud OBS` / `Qiniu Cloud` / `MinIo`) that are compatible with Amazon S3 (Simple Storage Service) protocol. It provides a series of object storage operations based on the automated configuration feature of Spring Boot.

## Features

- Support: Tencent Cloud, Alibaba Cloud, Huawei Cloud, Qiniu Cloud, JD Cloud, MinIo
- Provide a series of basic web endpoints and swagger documentation, supporting freedom to enable
- - Provides large file chunk uploading
- - Provides file preview
- - ......
- Support cross-service file transfer `ossTemplate.put().transferObject()`
- Provide bucket cross-origin configuration `oss.cross=true`

## Version Basics

- JDK 1.8
- Spring Boot 2.x

## How to Use

### Add Dependencies

- Add the following dependency in your **Maven** project:

```xml
<dependency>
    <groupId>io.github.weimin96</groupId>
    <artifactId>oss-spring-starter</artifactId>
    <version>${lastVersion}</version>
</dependency>
```

- Add the following dependency in your **Gradle** project:

```gradle
dependencies {
  implementation 'io.github.weimin96:oss-spring-starter:${lastVersion}'
}
```

- Or add the following configuration in `application.yml`:
```yaml
oss:
  endpoint: https://xxx.com
  access-key: YOUR_ACCESS_KEY
  secret-key: YOUR_SECRET_KEY
  bucket-name: your-bucket-name
```

- Code Usage

```java
@Autowired
private OssTemplate template;

// upload file
ossTemplate.put().putObject("bucket", "1.jpg", new File("/data/1.jpg"));
```

## Parameter Configuration

Explanation of all configurations:

| Configuration        | Type     | Default Value | Description                  |
|----------------------|----------|---------------|------------------------------|
| oss.enable           | boolean  | true          | Enable OSS or not             |
| oss.endpoint         | String   |               | Endpoint of the OSS service   |
| oss.bucket-name      | String   |               | Bucket name                   |
| oss.access-key       | String   |               | Access key ID                 |
| oss.secret-key       | String   |               | Access secret key             |
| oss.cross            | boolean  | false         | Allow cross-origin or not      |
| oss.type             | String   |               | OSS type (options: obs/minio) |
| oss.http.prefix      | String   |               | Prefix of the endpoint URL    |
| oss.http.enable      | boolean  | true          | Enable web endpoints or not    |


