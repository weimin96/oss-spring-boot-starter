# oss-spring-boot-starter

[![Java CI](https://github.com/weimin96/oss-spring-boot-starter/actions/workflows/ci.yml/badge.svg)](https://github.com/weimin96/oss-spring-boot-starter/actions/workflows/ci.yml)
[![Coverage Status](https://coveralls.io/repos/github/weimin96/oss-spring-boot-starter/badge.svg?branch=main)](https://coveralls.io/github/weimin96/oss-spring-boot-starter?branch=main)
[![GitHub Release](https://img.shields.io/github/v/release/weimin96/oss-spring-boot-starter)](https://github.com/weimin96/oss-spring-boot-starter/releases/)
[![Maven Central Version](https://img.shields.io/maven-central/v/io.github.weimin96/oss-spring-boot3-starter)](https://repo1.maven.org/maven2/io/github/weimin96/oss-spring-boot3-starter/)
[![License](https://img.shields.io/:license-apache-brightgreen.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)

基于 AWS S3 SDK v2 的对象存储 Spring Boot Starter，支持 Spring Boot 2、Spring Boot 3、Spring Boot 4。项目按使用方式拆分为基础
Java API、内置 REST 接口、OpenAPI 注解元数据三类 Starter。

示例工程入口：[samples/README.md](samples/README.md)

## 能力概览

- 使用 `OssTemplate` 访问上传、查询、删除、流式解压、预签名 URL、标签、Bucket 管理能力。
- 支持 MinIO、腾讯云 COS、华为云 OBS 以及通用 S3 兼容服务。
- 可按需启用内置 REST 接口，路径统一挂载在 `${oss.http.prefix}/oss`。
- 可按需启用带 Swagger 注解元数据的控制器，便于接入宿主应用已有的 OpenAPI 工具链。
- Spring Boot 2 使用 `javax.servlet`，Spring Boot 3 和 Spring Boot 4 使用 `jakarta.servlet`。

## 选择依赖

当前源码版本为 `3.0.0`。

| 使用场景                    | Spring Boot 2                      | Spring Boot 3                      | Spring Boot 4                      |
|-------------------------|------------------------------------|------------------------------------|------------------------------------|
| 只使用 Java API            | `oss-spring-boot2-starter`         | `oss-spring-boot3-starter`         | `oss-spring-boot4-starter`         |
| 使用内置 REST 接口            | `oss-spring-boot2-web-starter`     | `oss-spring-boot3-web-starter`     | `oss-spring-boot4-web-starter`     |
| 使用带 OpenAPI 注解的 REST 接口 | `oss-spring-boot2-openapi-starter` | `oss-spring-boot3-openapi-starter` | `oss-spring-boot4-openapi-starter` |

基础 Starter 只创建 `OssTemplate`，不注册 REST 控制器。需要 HTTP 接口时请选择 Web Starter 或 OpenAPI Starter。

OpenAPI Starter 只提供 Swagger 注解元数据，不内置 Swagger UI。宿主应用可以继续使用自己的 springdoc 或其他 OpenAPI 集成。

Maven Central 会发布 17 个运行时模块以满足传递依赖解析，但应用侧通常只需要直接声明上表中的 9 个版本化 Starter 之一。

## 用户该直接选哪个模块

- 你的应用只想注入 `OssTemplate`，并由自己编写 Controller、服务封装和鉴权逻辑：选择对应版本的
  `oss-spring-boot2/3/4-starter`
- 你的应用希望直接复用内置 `/oss/**` HTTP 端点，但不需要 Swagger 或 OpenAPI 注解元数据：选择对应版本的
  `oss-spring-boot2/3/4-web-starter`
- 你的应用既要复用内置 `/oss/**` HTTP 端点，也要让 springdoc、Knife4j 或其他 OpenAPI 工具直接扫描到接口注解：选择对应版本的
  `oss-spring-boot2/3/4-openapi-starter`
- 只需要声明一种入口 Starter；`web-starter` 已经传递依赖基础 `starter`，`openapi-starter` 已经传递依赖 `web-starter`
- 不要直接声明 `oss-domain`、`oss-web-api`、`oss-core`、`oss-spring-boot*-autoconfigure`、`oss-spring-javax-web-support`、
  `oss-spring-jakarta-web-support`；这些模块是运行时支撑与版本适配层，面向 Starter 组合，不是应用侧的直接入口

## 安装

Spring Boot 3 基础 Java API 示例：

```xml
<dependency>
    <groupId>io.github.weimin96</groupId>
    <artifactId>oss-spring-boot3-starter</artifactId>
    <version>3.0.0</version>
</dependency>
```

Spring Boot 3 内置 REST 接口示例：

```xml
<dependency>
    <groupId>io.github.weimin96</groupId>
    <artifactId>oss-spring-boot3-web-starter</artifactId>
    <version>3.0.0</version>
</dependency>
```

Spring Boot 3 OpenAPI 注解接口示例：

```xml
<dependency>
    <groupId>io.github.weimin96</groupId>
    <artifactId>oss-spring-boot3-openapi-starter</artifactId>
    <version>3.0.0</version>
</dependency>
```

Web Starter 和 OpenAPI Starter 的 Spring Web、Validation 依赖在本项目中按 `provided` 处理。宿主 Web
应用需要已经引入以下依赖；如果你的应用已经有它们，不需要重复声明。

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

## 基础配置

```yaml
oss:
  enable: true
  endpoint: http://127.0.0.1:9000
  bucket-name: oss-sample
  auto-create-bucket: true
  access-key: minioadmin
  secret-key: minioadmin
  type: minio
  max-connections: 50
  connection-timeout: 10000
  throughput-in-gbps: 20.0
  part-size-in-mb: 10
  http:
    enable: true
    prefix: /api

spring:
  servlet:
    multipart:
      max-file-size: 500MB
      max-request-size: 500MB
```

只使用基础 Java API 时，可以不配置 `oss.http`，或保持 `oss.http.enable=false`。

## 配置项

| 配置项                      | 类型      | 默认值     | 说明                              |
|--------------------------|---------|---------|---------------------------------|
| `oss.enable`             | boolean | `false` | 是否启用自动配置                        |
| `oss.endpoint`           | String  | 无       | 对象存储服务端点，启用后必填                  |
| `oss.bucket-name`        | String  | 无       | 默认 Bucket 名称，多数默认 Bucket 操作需要配置 |
| `oss.auto-create-bucket` | boolean | `false` | 默认 Bucket 不存在时是否自动创建            |
| `oss.access-key`         | String  | 无       | 访问密钥 ID，启用后必填                   |
| `oss.secret-key`         | String  | 无       | 访问密钥，启用后必填                      |
| `oss.type`               | String  | 无       | 存储类型，常用值为 `minio`、`cos`、`obs`   |
| `oss.max-connections`    | int     | `50`    | 最大连接数配置项                        |
| `oss.connection-timeout` | long    | `10000` | 连接超时时间，单位毫秒                     |
| `oss.throughput-in-gbps` | double  | `20.0`  | AWS CRT S3 客户端目标吞吐量             |
| `oss.part-size-in-mb`    | int     | `10`    | 分片大小，最小值为 5 MB                  |
| `oss.http.enable`        | boolean | `false` | 是否注册内置 REST 接口                  |
| `oss.http.prefix`        | String  | 空字符串    | REST 接口路径前缀                     |

## 存储类型

| `oss.type` | 对象存储              | URL 拼接方式                                           |
|------------|-------------------|----------------------------------------------------|
| `minio`    | MinIO 或本地 S3 兼容服务 | Path-Style，格式为 `{endpoint}/{bucket}/`              |
| `cos`      | 腾讯云 COS           | Virtual-Hosted，格式为 `{protocol}://{bucket}.{host}/` |
| `obs`      | 华为云 OBS           | Virtual-Hosted，格式为 `{protocol}://{bucket}.{host}/` |
| 其他值或空值     | 通用 S3 兼容服务        | Path-Style 兜底                                      |

底层 S3 客户端目前只在 `oss.type=minio` 时强制 Path-Style 寻址。使用其他 S3 兼容服务时，请先在目标环境验证 endpoint 与
Bucket 寻址方式。

## Java API 用法

所有操作都从 `OssTemplate` 进入。

| 入口                      | 能力                                        |
|-------------------------|-------------------------------------------|
| `ossTemplate.put()`     | 上传文件、创建目录占位、复制、移动、分片上传                    |
| `ossTemplate.query()`   | 连接测试、对象元数据、列表、树形结构、下载、预览                  |
| `ossTemplate.delete()`  | 单个删除、批量删除、目录递归删除                          |
| `ossTemplate.unzip()`   | ZIP 流式解压、跨 Bucket 解压、按条目前缀过滤解压            |
| `ossTemplate.presign()` | 生成 GET/PUT 预签名 URL                        |
| `ossTemplate.tagging()` | 对象标签和 Bucket 标签                           |
| `ossTemplate.bucket()`  | Bucket 详情、ACL、版本控制、时间回滚、生命周期、CORS、策略、安全配置 |

上传示例：

```java
@RestController
@RequiredArgsConstructor
public class FileController {

    private final OssTemplate ossTemplate;

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            ObjectInfo objectInfo = ossTemplate.put()
                    .putObject("uploads/", file.getOriginalFilename(), inputStream);
            return objectInfo.getUrl();
        }
    }
}
```

查询与预签名示例：

```java
ObjectInfo objectInfo = ossTemplate.query().getObjectInfo("uploads/demo.txt");

boolean exists = ossTemplate.query().checkExist("uploads/demo.txt");

String downloadUrl = ossTemplate.presign()
        .generateGetPresignedUrl("uploads/demo.txt", Duration.ofMinutes(10));
```

## 内置 REST 接口

启用条件：

- 引入对应版本的 Web Starter 或 OpenAPI Starter。
- 宿主应用是 Spring Web 应用。
- 配置 `oss.enable=true`。
- 配置 `oss.http.enable=true`。

所有路径都以 `${oss.http.prefix}/oss` 为前缀。示例配置 `oss.http.prefix=/api` 时，完整前缀为 `/api/oss`。

除预览和下载接口直接输出文件流外，其他接口统一返回 `OssResponse`。

### 分片上传

| 方法     | 路径                 | 说明        |
|--------|--------------------|-----------|
| `POST` | `/multipart/init`  | 初始化分片上传任务 |
| `POST` | `/multipart/chunk` | 上传单个分片    |
| `POST` | `/multipart/merge` | 合并分片      |
| `GET`  | `/multipart/parts` | 查询已上传分片列表 |

### 文件上传与删除

| 方法       | 路径         | 说明       |
|----------|------------|----------|
| `POST`   | `/object`  | 上传单个文件   |
| `POST`   | `/folder`  | 创建目录占位对象 |
| `DELETE` | `/object`  | 删除单个对象   |
| `DELETE` | `/objects` | 批量删除对象   |
| `DELETE` | `/folder`  | 递归删除目录   |

### 文件查询

| 方法     | 路径                             | 说明                  |
|--------|--------------------------------|---------------------|
| `GET`  | `/object`                      | 查询对象元数据             |
| `GET`  | `/object/exists`               | 检查对象是否存在            |
| `GET`  | `/object/list`                 | 递归列举对象              |
| `GET`  | `/object/list/next-level`      | 列举下一层级文件和目录         |
| `GET`  | `/object/list/lazy`            | 游标分页懒加载列表           |
| `GET`  | `/object/tree`                 | 获取目录树               |
| `GET`  | `/object/tree/search`          | 按关键字搜索目录树           |
| `GET`  | `/object/tree/folder`          | 获取仅包含目录的树           |
| `GET`  | `/buckets`                     | 列举当前凭证可见的 Bucket    |
| `GET`  | `/buckets/{bucketName}`        | 查询 Bucket 聚合详情      |
| `GET`  | `/buckets/{bucketName}/access` | 查询 Bucket ACL       |
| `PUT`  | `/buckets/{bucketName}/access` | 设置 Bucket ACL       |
| `POST` | `/buckets/{bucketName}/rewind` | 按时间回滚 Bucket 当前可见状态 |
| `GET`  | `/connect`                     | 测试默认 Bucket 连通性     |

### 预览、下载、复制、移动

| 方法     | 路径                    | 说明                      |
|--------|-----------------------|-------------------------|
| `GET`  | `/object/preview/**`  | 内联预览对象，支持 HTTP Range    |
| `GET`  | `/object/download/**` | 以附件方式下载对象，支持 HTTP Range |
| `POST` | `/object/copy`        | 复制对象                    |
| `POST` | `/object/move`        | 移动对象到目标目录               |

### 解压与预签名

| 方法     | 路径                    | 说明                   |
|--------|-----------------------|----------------------|
| `POST` | `/unzip`              | 在默认 Bucket 内流式解压 ZIP |
| `POST` | `/unzip/cross-bucket` | 跨 Bucket 流式解压 ZIP    |
| `POST` | `/unzip/filter`       | 按条目前缀过滤解压            |
| `GET`  | `/presign/get`        | 生成下载预签名 URL          |
| `GET`  | `/presign/put`        | 生成上传预签名 URL          |

### 标签与 Bucket 管理

| 方法       | 路径                             | 说明              |
|----------|--------------------------------|-----------------|
| `GET`    | `/object/tags`                 | 获取对象标签          |
| `PUT`    | `/object/tags`                 | 覆盖设置对象标签        |
| `PATCH`  | `/object/tags`                 | 合并更新对象标签        |
| `DELETE` | `/object/tags`                 | 删除对象标签          |
| `POST`   | `/bucket`                      | 创建 Bucket       |
| `GET`    | `/bucket/versioning`           | 查询版本控制状态        |
| `PUT`    | `/bucket/versioning/enable`    | 启用版本控制          |
| `PUT`    | `/bucket/versioning/suspend`   | 挂起版本控制          |
| `GET`    | `/bucket/lifecycle`            | 查询生命周期规则        |
| `POST`   | `/bucket/lifecycle/expiration` | 添加过期删除规则        |
| `DELETE` | `/bucket/lifecycle`            | 删除生命周期规则        |
| `GET`    | `/bucket/cors`                 | 查询 CORS 配置      |
| `PUT`    | `/bucket/cors/allow-all`       | 设置允许所有来源的 CORS  |
| `DELETE` | `/bucket/cors`                 | 删除 CORS 配置      |
| `GET`    | `/bucket/policy`               | 查询 Bucket 策略    |
| `PUT`    | `/bucket/policy`               | 设置 Bucket 策略    |
| `DELETE` | `/bucket/policy`               | 删除 Bucket 策略    |
| `PUT`    | `/bucket/encryption/enable`    | 启用 SSE-S3 服务端加密 |
| `PUT`    | `/bucket/public-access/block`  | 开启公共访问屏蔽        |
| `GET`    | `/bucket/tags`                 | 获取 Bucket 标签    |
| `PUT`    | `/bucket/tags`                 | 覆盖设置 Bucket 标签  |
| `DELETE` | `/bucket/tags`                 | 删除 Bucket 标签    |

## 运行示例工程

启动本地 MinIO：

```powershell
docker run -d --name minio -p 9000:9000 -p 9001:9001 -e MINIO_ROOT_USER=minioadmin -e MINIO_ROOT_PASSWORD=minioadmin minio/minio server /data --console-address ":9001"
```

运行 Spring Boot 3 示例：

```powershell
Set-Location samples\sample-springboot3
mvn spring-boot:run
```

示例默认配置：

- 应用端口：`8080`
- MinIO 端点：`http://127.0.0.1:9000`
- MinIO 控制台：`http://127.0.0.1:9001`
- 默认 Bucket：`oss-sample`
- REST 前缀：`/api/oss`

验证连通性：

```powershell
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/api/oss/connect'
```

## 从源码构建

```powershell
# 编译全部模块
mvn compile

# 执行全部测试
mvn test

# 只测试 Spring Boot 3 Web Starter 及其依赖
mvn -pl oss-spring-boot3-web-starter -am test

# 安装到本地 Maven 仓库
mvn install
```

集成测试和示例运行依赖可访问的对象存储服务。默认示例按本地 MinIO 配置。

## 使用注意事项

- `oss.enable=true` 是所有自动配置的总开关。
- `oss.http.enable=true` 只在 Web Starter 或 OpenAPI Starter 中注册 HTTP 控制器。
- `bucket-name` 是默认 Bucket 名称；如果使用默认 Bucket 操作，建议显式配置。
- `auto-create-bucket=true` 会在默认 Bucket 不存在时尝试创建 Bucket。
- 分片大小不能小于 5 MB。
- 预览和下载接口支持 HTTP Range，适合视频、音频等大文件场景。
- Bucket ACL、公共访问屏蔽、加密、生命周期等能力取决于底层对象存储是否实现对应 S3 API。
- 生产环境不要把访问密钥明文写入代码仓库，建议使用环境变量、配置中心或密钥管理系统。

## 许可证

[Apache License 2.0](LICENSE)
