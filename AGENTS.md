# AGENTS.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

基于 AWS S3 SDK v2 的对象存储 Spring Boot Starter，支持 Spring Boot 2.x / 3.x / 4.x 多版本。

## 构建命令

```bash
# 编译整个项目
mvn compile

# 运行测试
mvn test

# 编译特定模块（oss-core 使用 Java 8 编译）
mvn compile -Dmaven.compiler.source=1.8 -Dmaven.compiler.target=1.8 -pl oss-core

# 安装到本地仓库
mvn install

# 跳过测试
mvn install -DskipTests
```

## 模块结构

```
oss-spring-boot-starter/
├── oss-core/                 # 核心模块（Java 8）
├── oss-spring-boot2-starter/ # Spring Boot 2.x 适配
├── oss-spring-boot3-starter/ # Spring Boot 3.x 适配
├── oss-spring-boot4-starter/# Spring Boot 4.x 适配
└── samples/                  # 示例项目
```

## 代码架构

**入口类**: `OssTemplate`（门面模式）
- `put()` → `PutOperations` - 上传、复制、移动、分片上传
- `query()` → `QueryOperations` - 列举、树形、下载、预览
- `delete()` → `DeleteOperations` - 单个、批量、文件夹删除
- `unzip()` → `StreamUnzipOperations` - ZIP 流式解压
- `presign()` → `PresignOperations` - 预签名 URL
- `tagging()` → `TaggingOperations` - 标签管理
- `bucket()` → `BucketOperations` - 版本控制、生命周期、CORS

**基类**: `Operations` - 提供模板方法、策略模式、异常处理

**策略模式**: `DomainStrategyFactory` + `DomainStrategy` 实现类，处理不同 OSS 的域名格式（Virtual-Hosted / Path-Style）

## JDK 8 兼容性要求

`oss-core` 模块必须保持 JDK 8 兼容，修改时避免使用：
- `List.of()`, `Set.of()`, `Map.of()` → 用 `Collections.unmodifiableList(Arrays.asList())` 等
- `instanceof Type var` 模式匹配 → 用强制类型转换
- `InputStream.readAllBytes()` → 用自定义工具方法
- `.toList()` → 用 `.collect(Collectors.toList())`