# oss-spring-boot-starter

[![Java CI](https://github.com/weimin96/oss-spring-boot-starter/actions/workflows/ci.yml/badge.svg)](https://github.com/weimin96/oss-spring-boot-starter/actions/workflows/ci.yml)
[![Coverage Status](https://coveralls.io/repos/github/weimin96/oss-spring-boot-starter/badge.svg?branch=main)](https://coveralls.io/github/weimin96/oss-spring-boot-starter?branch=main)
[![GitHub Release](https://img.shields.io/github/v/release/weimin96/oss-spring-boot-starter)](https://github.com/weimin96/oss-spring-boot-starter/releases/)
[![Maven Central Version](https://img.shields.io/maven-central/v/io.github.weimin96/oss-spring-boot3-starter)](https://repo1.maven.org/maven2/io/github/weimin96/oss-spring-boot3-starter/)
[![GitHub repo size](https://img.shields.io/github/repo-size/weimin96/oss-spring-boot-starter)](https://github.com/weimin96/oss-spring-boot-starter/releases/)
[![License](https://img.shields.io/:license-apache-brightgreen.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![Last Commit](https://img.shields.io/github/last-commit/weimin96/oss-spring-boot-starter.svg)](https://github.com/weimin96/oss-spring-boot-starter)
[![GitHub commit activity](https://img.shields.io/github/commit-activity/m/weimin96/oss-spring-boot-starter.svg)](https://github.com/weimin96/oss-spring-boot-starter)

基于 AWS S3 SDK v2 的对象存储 Spring Boot Starter，支持 **Spring Boot 2.x / 3.x / 4.x** 多版本。

示例入口：[samples/README.md](samples/README.md)

## 简介

所有兼容 S3 协议的存储服务均可直接使用：

| 云厂商 | type 值 |
|---|---|
| Amazon S3 | *(留空)* |
| 腾讯云 COS | `cos` |
| 阿里云 OSS | `oss` |
| 华为云 OBS | `obs` |
| 七牛云 Kodo | `qiniu` |
| 京东云 OSS | `jd` |
| MinIO | `minio` |

## 快速开始

**1. 引入依赖**

Spring Boot 2.x



```xml
<dependency>
    <groupId>io.github.weimin96</groupId>
    <artifactId>oss-spring-boot2-starter</artifactId>
    <version>3.0.0</version>
</dependency>
```

Spring Boot 3.x

```xml
<dependency>
    <groupId>io.github.weimin96</groupId>
    <artifactId>oss-spring-boot3-starter</artifactId>
    <version>3.0.0</version>
</dependency>
```

Spring Boot 4.x

```xml
<dependency>
    <groupId>io.github.weimin96</groupId>
    <artifactId>oss-spring-boot4-starter</artifactId>
    <version>3.0.0</version>
</dependency>
```

**2. 配置 `application.yml`**

```yaml
oss:
  enable: true
  endpoint: http://localhost:9000
  bucket-name: my-bucket
  auto-create-bucket: true
  access-key: minioadmin
  secret-key: minioadmin
  type: minio
  throughput-in-gbps: 20.0
  part-size-in-mb: 10
  # 启用内置 REST 端点（可选）
  http:
    enable: true
    prefix: /api

# 配置文件上传限制大小
spring:
  servlet:
    multipart:
      max-file-size: 2GB # 根据需求自行配置
      max-request-size: 2GB # 根据需求自行配置
```

**3. 注入使用**

```java
@RestController
@RequiredArgsConstructor
public class FileController {

    private final OssTemplate ossTemplate;

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file) throws IOException {
        ObjectInfo info = ossTemplate.put().putObject(
                "uploads/", file.getOriginalFilename(), file.getInputStream());
        return info.getUrl();
    }
}
```

---

## 配置参数

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `oss.enable` | boolean | `false` | 是否启用 OSS |
| `oss.endpoint` | String | — | 存储服务端点（**必填**） |
| `oss.bucket-name` | String | — | 默认 Bucket 名称 |
| `oss.auto-create-bucket` | boolean | `false` | Bucket 不存在时自动创建 |
| `oss.access-key` | String | — | Access Key（**必填**） |
| `oss.secret-key` | String | — | Secret Key（**必填**） |
| `oss.type` | String | — | 存储类型：`minio` / `cos` / `obs` / `oss` / `s3` |
| `oss.max-connections` | int | `50` | 最大连接数 |
| `oss.connection-timeout` | long | `10000` | 连接超时（毫秒） |
| `oss.throughput-in-gbps` | double | `20.0` | 目标吞吐量（Gbps） |
| `oss.part-size-in-mb` | int | `10` | 分片上传分片大小（MB，最小 5） |
| `oss.http.enable` | boolean | `false` | 启用内置 REST 端点 |
| `oss.http.prefix` | String | `""` | REST 端点路径前缀 |

---

## 内置 REST API（`oss.http.enable=true` 时可用）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/oss/object` | 上传文件 |
| DELETE | `/oss/object` | 删除单个文件 |
| DELETE | `/oss/objects` | 批量删除文件 |
| GET | `/oss/object` | 获取文件元数据 |
| GET | `/oss/object/list` | 列举目录下所有对象 |
| GET | `/oss/object/list/next-level` | 列举下一层级 |
| GET | `/oss/object/list/lazy` | 懒加载分页列表 |
| GET | `/oss/object/tree` | 获取目录树 |
| GET | `/oss/object/preview/**` | 在线预览文件（支持 Range） |
| GET | `/oss/object/download/**` | 下载文件 |
| POST | `/oss/multipart/init` | 初始化分片上传 |
| POST | `/oss/multipart/chunk` | 上传分片 |
| POST | `/oss/multipart/merge` | 合并分片 |
| GET | `/oss/presign/get` | 生成下载预签名 URL |
| GET | `/oss/presign/put` | 生成上传预签名 URL |
| POST | `/oss/unzip` | 流式解压 ZIP 到 OSS |
| GET/PUT/DELETE | `/oss/object/tags` | 对象标签管理 |
| GET/PUT/DELETE | `/oss/bucket/versioning` | Bucket 版本控制 |
| GET/DELETE | `/oss/bucket/lifecycle` | 生命周期规则 |
| GET/DELETE | `/oss/bucket/cors` | CORS 配置 |

---

## 支持的存储类型

| 类型值 | 存储服务 | 域名策略 |
|--------|----------|----------|
| `minio` | MinIO | 路径风格（Path Style） |
| `cos` | 腾讯云 COS | 虚拟主机风格 |
| `obs` | 华为云 OBS | 虚拟主机风格 |
| `oss` | 阿里云 OSS | 虚拟主机风格 |
| `s3` | Amazon S3 | 虚拟主机风格 |
| 其他/不填 | 通用 S3 兼容 | 路径风格 |

---

## 版本对应关系

| Starter 版本 | Spring Boot | Java | Servlet API |
|-------------|-------------|------|-------------|
| `oss-spring-boot2-starter` | 2.3 ~ 2.7.x | 8+ | `javax.servlet` |
| `oss-spring-boot3-starter` | 3.0 ~ 3.x | 17+ | `jakarta.servlet` |
| `oss-spring-boot4-starter` | 4.0+ | 21+ | `jakarta.servlet` |

---

## Java API

所有操作通过 `OssTemplate` 以命名空间模式访问：

```
ossTemplate.put()      → PutOperations           上传、复制、移动、分片上传
ossTemplate.query()    → QueryOperations          列举、树形、下载、预览
ossTemplate.delete()   → DeleteOperations         单个、批量、文件夹删除
ossTemplate.unzip()    → StreamUnzipOperations    ZIP 流式解压
ossTemplate.presign()  → PresignOperations        预签名 GET / PUT URL
ossTemplate.tagging()  → TaggingOperations        对象与 Bucket 标签管理
ossTemplate.bucket()   → BucketOperations         版本控制、生命周期、CORS、策略、加密
```

## 内置 REST 端点

配置 `oss.http.enable: true` 后自动开启。所有路径以 `${oss.http.prefix}/oss` 为前缀。

### 文件上传

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/oss/object` | 上传单个文件 |
| `POST` | `/oss/folder` | 创建文件夹占位符 |
| `POST` | `/oss/multipart/init` | 初始化分片上传，返回 uploadId |
| `POST` | `/oss/multipart/chunk` | 上传单个分片 |
| `POST` | `/oss/multipart/merge` | 合并分片，完成上传 |
| `GET` | `/oss/multipart/parts` | 查询已上传分片列表（断点续传） |

### 文件查询

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/oss/object` | 获取对象元数据 |
| `GET` | `/oss/object/exists` | 检查对象是否存在 |
| `GET` | `/oss/object/list` | 列举路径下所有对象（递归） |
| `GET` | `/oss/object/list/next-level` | 列举下一层级文件和文件夹 |
| `GET` | `/oss/object/list/lazy` | 分页懒加载列表 |
| `GET` | `/oss/object/tree` | 获取完整目录树 |
| `GET` | `/oss/object/tree/search` | 按关键字搜索目录树 |
| `GET` | `/oss/object/tree/folder` | 仅返回文件夹节点的树 |
| `GET` | `/oss/buckets` | 列举所有 Bucket |
| `GET` | `/oss/connect` | 测试 OSS 连接状态 |

### 文件预览与下载

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/oss/object/preview/**` | 内联预览（支持 HTTP Range，适合视频/音频） |
| `GET` | `/oss/object/download/**` | 强制下载（Content-Disposition: attachment） |

### 文件操作

| 方法 | 路径 | 说明 |
|---|---|---|
| `DELETE` | `/oss/object` | 删除单个对象 |
| `DELETE` | `/oss/objects` | 批量删除（请求体：key 列表） |
| `DELETE` | `/oss/folder` | 递归删除文件夹 |
| `POST` | `/oss/object/copy` | 同 Bucket 内复制文件 |
| `POST` | `/oss/object/move` | 移动文件到指定目录 |

### 流式解压

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/oss/unzip` | 解压 ZIP 对象到目标路径 |
| `POST` | `/oss/unzip/cross-bucket` | 跨 Bucket 解压 |
| `POST` | `/oss/unzip/filter` | 按条目前缀过滤解压 |

### 预签名 URL

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/oss/presign/get` | 生成预签名下载链接 |
| `GET` | `/oss/presign/put` | 生成预签名客户端直传链接 |

### 对象标签

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/oss/object/tags` | 获取对象标签 |
| `PUT` | `/oss/object/tags` | 设置对象标签（覆盖） |
| `PATCH` | `/oss/object/tags` | 合并/更新对象标签 |
| `DELETE` | `/oss/object/tags` | 删除对象所有标签 |

### Bucket 管理

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/oss/bucket` | 创建 Bucket |
| `GET` | `/oss/bucket/versioning` | 获取版本控制状态 |
| `PUT` | `/oss/bucket/versioning/enable` | 启用版本控制 |
| `PUT` | `/oss/bucket/versioning/suspend` | 挂起版本控制 |
| `GET` | `/oss/bucket/lifecycle` | 获取生命周期规则 |
| `POST` | `/oss/bucket/lifecycle/expiration` | 添加过期删除规则 |
| `DELETE` | `/oss/bucket/lifecycle` | 删除所有生命周期规则 |
| `GET` | `/oss/bucket/cors` | 获取 CORS 配置 |
| `PUT` | `/oss/bucket/cors/allow-all` | 设置允许所有来源的 CORS |
| `DELETE` | `/oss/bucket/cors` | 删除 CORS 配置 |
| `GET` | `/oss/bucket/policy` | 获取 Bucket 访问策略 |
| `PUT` | `/oss/bucket/policy` | 设置 Bucket 访问策略 |
| `DELETE` | `/oss/bucket/policy` | 删除 Bucket 访问策略 |
| `PUT` | `/oss/bucket/encryption/enable` | 启用 SSE-S3 服务端加密 |
| `PUT` | `/oss/bucket/public-access/block` | 屏蔽所有公共访问 |
| `GET` | `/oss/bucket/tags` | 获取 Bucket 标签 |
| `PUT` | `/oss/bucket/tags` | 设置 Bucket 标签 |
| `DELETE` | `/oss/bucket/tags` | 删除 Bucket 所有标签 |

---

## License

[Apache License 2.0](LICENSE)
