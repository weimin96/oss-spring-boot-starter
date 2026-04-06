# AGEENT.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

`oss-spring-boot3-starter` 是一个基于 Spring Boot 3.x 和 AWS SDK 的对象存储服务（OSS）工具库，兼容支持 S3 协议的主流云存储：腾讯云 COS、阿里云 OSS、华为云 OBS、七牛云、京东云、MinIO。

## Build Commands

```bash
# 构建项目
mvn clean install

# 跳过测试构建
mvn clean install -DskipTests

# 运行单个测试类
mvn test -Dtest=ClassName

# 运行单个测试方法
mvn test -Dtest=ClassName#methodName

# 运行集成测试（需要 MinIO 容器）
mvn test -Dtest=OssTemplateMinioIntegrationTest

# 发布发布版本（需要 GPG 密钥）
mvn clean install -Prelease -DskipTests
```

## Architecture

### 核心模块

```
com.wiblog.oss
├── service/
│   ├── OssTemplate.java       # 门面类，管理 S3AsyncClient 生命周期
│   ├── PutOperations.java     # 文件上传操作
│   ├── QueryOperations.java   # 文件查询操作
│   └── DeleteOperations.java  # 文件删除操作
├── config/
│   └── OssAutoConfiguration.java  # Spring Boot 自动配置
├── bean/
│   └── OssProperties.java     # 配置属性类
├── controller/
│   └── OssController.java     # HTTP REST 端点（可选启用）
├── strategy/                  # 策略模式实现域名访问方式
│   ├── DomainStrategy.java
│   ├── PathStyleDomainStrategy.java
│   └── VirtualHostedDomainStrategy.java
└── exception/
    └── OssException.java      # 自定义异常
```

### 设计要点

- **门面模式**：`OssTemplate` 是核心入口，通过 `put()`、`query()`、`delete()` 方法返回具体操作类
- **生命周期管理**：`start()` 和 `stop()` 方法支持动态启停 OSS 客户端
- **策略模式**：`DomainStrategyFactory` 根据 OSS 类型选择合适的域名访问策略（Path-Style 或 Virtual-Hosted Style）
- **自动配置**：通过 `@ConditionalOnProperty` 控制组件是否加载，支持按需启用

## Configuration

在 `application.yml` 中配置：

```yaml
oss:
  enable: true
  endpoint: https://xxx.com
  access-key: YOUR_ACCESS_KEY
  secret-key: YOUR_SECRET_KEY
  bucket-name: your-bucket
  type: minio  # 支持: cos/oss/obs/minio
  http:
    enable: true  # 可选：启用 REST API 端点
```

## Version Requirements

- JDK 21
- Spring Boot 3.x
- Maven 3.9+