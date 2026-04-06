# OSS Spring Boot3 Starter

[![Java CI](https://github.com/weimin96/oss-spring-boot-starter/actions/workflows/ci.yml/badge.svg)](https://github.com/weimin96/oss-spring-boot-starter/actions/workflows/ci.yml)
[![Coverage Status](https://coveralls.io/repos/github/weimin96/oss-spring-boot-starter/badge.svg?branch=main)](https://coveralls.io/github/weimin96/oss-spring-boot-starter?branch=main)
[![GitHub Release](https://img.shields.io/github/v/release/weimin96/oss-spring-boot-starter)](https://github.com/weimin96/oss-spring-boot-starter/releases/)
[![Maven Central Version](https://img.shields.io/maven-central/v/io.github.weimin96/oss-spring-boot3-starter)](https://repo1.maven.org/maven2/io/github/weimin96/oss-spring-boot3-starter/)
[![GitHub repo size](https://img.shields.io/github/repo-size/weimin96/oss-spring-boot-starter)](https://github.com/weimin96/oss-spring-boot-starter/releases/)
[![License](https://img.shields.io/:license-apache-brightgreen.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![Last Commit](https://img.shields.io/github/last-commit/weimin96/oss-spring-boot-starter.svg)](https://github.com/weimin96/oss-spring-boot-starter)
[![GitHub commit activity](https://img.shields.io/github/commit-activity/m/weimin96/oss-spring-boot-starter.svg)](https://github.com/weimin96/oss-spring-boot-starter)

README: [English](README.md) | [中文](README-zh-CN.md)

Wiki: [Wiki](https://github.com/weimin96/oss-spring-boot-starter/wiki)

示例入口：[samples/README.md](samples/README.md)

---

## 简介

`oss-spring-boot-starter` 是一个基于 Spring Boot 3 自动配置的对象存储操作库。底层使用 **AWS SDK for Java v2**（CRT 异步客户端 + S3 Transfer Manager），对外提供简洁的 Java API 以及一套可选的开箱即用 REST 端点。

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

---

## 环境要求

- **JDK 21+**
- **Spring Boot 3.x**

> Spring Boot 2.x 版本请使用 [`spring2` 分支](https://github.com/weimin96/oss-spring-boot-starter/tree/spring2)。

---

## 快速上手

### 1. 引入依赖

**Maven**
```xml
<dependency>
    <groupId>io.github.weimin96</groupId>
    <artifactId>oss-spring-boot3-starter</artifactId>
    <version>${lastVersion}</version>
</dependency>
```

**Gradle**
```gradle
dependencies {
    implementation 'io.github.weimin96:oss-spring-boot3-starter:${lastVersion}'
}
```

### 2. 配置 `application.yml`

```yaml
oss:
  enable: true
  endpoint: https://s3.ap-northeast-1.amazonaws.com   # 或 MinIO / COS / OBS 地址
  access-key: YOUR_ACCESS_KEY
  secret-key: YOUR_SECRET_KEY
  bucket-name: my-bucket
  type: minio          # AWS S3 可留空；MinIO 填 minio，腾讯云填 cos，以此类推
  auto-create-bucket: false
  throughput-in-gbps: 20.0
  part-size-in-mb: 10
  http:
    enable: true       # 开启内置 REST 端点
    prefix: /api       # 可选 URL 前缀，开启后访问路径为 /api/oss/...
```

### 3. 注入并使用

```java
@Autowired
private OssTemplate ossTemplate;

// 上传文件
ossTemplate.put().putObject("images/", "photo.jpg", new File("/tmp/photo.jpg"));

// 下载为 InputStream
InputStream in = ossTemplate.query().getInputStream("images/photo.jpg");

// 生成预签名下载链接（1小时有效）
String url = ossTemplate.presign().generateGetPresignedUrl("images/photo.jpg", Duration.ofHours(1));

// 流式解压 ZIP，无需本地落盘
UnzipResult result = ossTemplate.unzip().unzip("archives/data.zip", "extracted/");
```

---

## 参数配置说明

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `oss.enable` | boolean | `false` | 总开关，必须设为 `true` 才会初始化。 |
| `oss.endpoint` | String | — | S3 兼容服务端点 URL（**必填**）。 |
| `oss.access-key` | String | — | 访问密钥 ID（**必填**）。 |
| `oss.secret-key` | String | — | 访问密钥（**必填**）。 |
| `oss.bucket-name` | String | — | 默认 Bucket 名称。 |
| `oss.auto-create-bucket` | boolean | `false` | Bucket 不存在时自动创建。 |
| `oss.type` | String | — | 存储类型提示：`minio`、`cos`、`obs`、`oss`、`qiniu` 等。 |
| `oss.max-connections` | int | `50` | HTTP 最大并发连接数。 |
| `oss.connection-timeout` | long | `10000` | 连接超时，单位毫秒。 |
| `oss.throughput-in-gbps` | double | `20.0` | CRT 异步客户端目标吞吐量（Gbps）。 |
| `oss.part-size-in-mb` | int | `10` | 分片上传最小分片大小（MB，S3 协议最小 5 MB）。 |
| `oss.http.enable` | boolean | `false` | 是否开启内置 REST 端点。 |
| `oss.http.prefix` | String | `""` | 内置端点 URL 前缀。 |

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

### 上传操作（`put()`）

```java
// 从 InputStream 上传
ossTemplate.put().putObject("folder/", "file.txt", inputStream);

// 从 File 上传（指定完整 key）
ossTemplate.put().putObjectForKey("images/photo.jpg", new File("/tmp/photo.jpg"));

// 创建空文件夹占位符
ossTemplate.put().mkdirs("folder/subfolder/");

// 上传本地目录（isIncludeFolderName=true 时目录名会包含在远程路径中）
ossTemplate.put().putFolder("remote/path/", new File("/local/dir"), true);

// 同 Bucket 内复制文件
ossTemplate.put().copyFile("src/a.txt", "dst/a.txt");

// 移动文件（复制后删除源文件）
ossTemplate.put().move("src/a.txt", "dst/");
```

### 分片上传（`put()`）

```java
// 1. 初始化，获取 uploadId
String uploadId = ossTemplate.put().initTask(chunkTask);

// 2. 逐片上传（支持并发）
ChunkTarget part = ossTemplate.put().chunk(chunk);

// 3. 合并分片，完成上传
ObjectInfo info = ossTemplate.put().merge(chunkMerge);

// 查询已上传的分片（用于断点续传）
List<Part> parts = ossTemplate.put().listParts(bucketName, objectKey, uploadId);
```

### 查询与下载（`query()`）

```java
// 检查文件是否存在
boolean exists = ossTemplate.query().checkExist("images/photo.jpg");

// 获取文件元数据（大小、修改时间等）
ObjectInfo info = ossTemplate.query().getObjectInfo("images/photo.jpg");

// 列举路径下所有对象（递归）
List<ObjectInfo> list = ossTemplate.query().listObjects("images/");

// 下一层级列举（非递归，类似 ls）
List<ObjectTreeNode> nodes = ossTemplate.query().listNextLevel("images/");

// 分页懒加载（适合文件很多的场景）
LazyDataList<ObjectInfo> page = ossTemplate.query().lazyList("images/", 100, continuationToken);

// 获取完整目录树
ObjectTreeNode tree = ossTemplate.query().getTreeList("images/");

// 关键字搜索树
ObjectTreeNode result = ossTemplate.query().getTreeListByName("images/", "avatar");

// 读取文本内容
String text = ossTemplate.query().getContent("config/app.json");

// 下载到本地文件
File f = ossTemplate.query().getFile("images/photo.jpg", "/tmp/photo.jpg");

// 下载整个文件夹到本地
ossTemplate.query().getFolder("images/", "/tmp/images/");

// 通过 HTTP 响应输出文件（内联预览 / 附件下载，支持 Range 分段）
ossTemplate.query().previewObject(request, response, "images/photo.jpg", false);  // 预览
ossTemplate.query().previewObject(request, response, "video/demo.mp4", true);     // 下载
```

### 删除操作（`delete()`）

```java
// 删除单个对象
ossTemplate.delete().removeObject("images/photo.jpg");

// 递归删除文件夹下所有对象
ossTemplate.delete().removeFolder("tmp/uploads/");
```

### 流式解压（`unzip()`）

从 S3 流式读取 ZIP 文件，边下载边解压边回写，**全程无本地磁盘落地**，适合大文件解压场景。

```java
// 解压到同 Bucket 的目标路径
UnzipResult result = ossTemplate.unzip().unzip("archives/data.zip", "extracted/");

// 跨 Bucket 解压
UnzipResult result = ossTemplate.unzip().unzip(
        "src-bucket", "data.zip", "dst-bucket", "out/");

// 按前缀过滤，只解压名称以 "docs/" 开头的条目
UnzipResult result = ossTemplate.unzip().unzipWithFilter(
        "data.zip", "docs/", "extracted/docs/");

// 自定义处理每个条目（例如写入本地或转发给 HTTP 响应）
ossTemplate.unzip().unzip("data.zip", (entry, stream) -> {
    // 直接使用 stream，不要关闭它，框架统一管理
    Files.copy(stream, Paths.get("/tmp/" + entry.getName()));
});

// 查看解压结果
System.out.println("成功：" + result.getSucceededCount());
System.out.println("失败：" + result.getFailed());  // 失败的条目名称列表
```

### 预签名 URL（`presign()`）

```java
// 生成临时下载链接（30 分钟有效），无需鉴权即可访问
String downloadUrl = ossTemplate.presign()
        .generateGetPresignedUrl("images/photo.jpg", Duration.ofMinutes(30));

// 生成前端直传 URL（PUT 方式，15 分钟有效），文件直接从浏览器上传，不经过后端
String uploadUrl = ossTemplate.presign()
        .generatePutPresignedUrl("uploads/new.jpg", "image/jpeg",
                Duration.ofMinutes(15), null);
```

### 对象标签（`tagging()`）

```java
// 设置标签（覆盖所有已有标签，每个对象最多 10 个）
ossTemplate.tagging().setObjectTags("file.jpg",
        Map.of("env", "prod", "owner", "alice"));

// 合并标签（保留现有标签，仅更新/新增指定键）
ossTemplate.tagging().mergeObjectTags("file.jpg",
        Map.of("reviewed", "true"));

// 获取标签
Map<String, String> tags = ossTemplate.tagging().getObjectTags("file.jpg");

// 删除所有标签
ossTemplate.tagging().deleteObjectTags("file.jpg");

// Bucket 级别标签
ossTemplate.tagging().setBucketTags(Map.of("project", "my-app"));
ossTemplate.tagging().getBucketTags();
ossTemplate.tagging().deleteBucketTags();
```

### Bucket 管理（`bucket()`）

```java
// 版本控制
ossTemplate.bucket().enableVersioning();
ossTemplate.bucket().suspendVersioning();
String status = ossTemplate.bucket().getVersioningStatus(); // "Enabled" / "Suspended" / null

// 生命周期规则：tmp/ 下的对象 7 天后自动删除
ossTemplate.bucket().addExpirationRule("expire-tmp", "tmp/", 7);
List<LifecycleRule> rules = ossTemplate.bucket().getLifecycleRules();
ossTemplate.bucket().deleteLifecycleRules();

// CORS（前端直传场景：允许所有来源）
ossTemplate.bucket().allowAllOriginsCors();
ossTemplate.bucket().deleteCorsRules();

// Bucket 访问策略（IAM Policy JSON）
String policy = ossTemplate.bucket().getBucketPolicy();
ossTemplate.bucket().putBucketPolicy(policyJson);
ossTemplate.bucket().deleteBucketPolicy();

// 服务端加密（SSE-S3 / AES-256）
ossTemplate.bucket().enableServerSideEncryption();

// 屏蔽所有公共访问（最高安全级别）
ossTemplate.bucket().blockAllPublicAccess();
```

---

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

## 开源协议

[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0.html)
