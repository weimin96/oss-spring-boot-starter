# AGENTS.md

## Project Overview

基于 AWS S3 SDK v2 的对象存储 Spring Boot Starter，支持 **Spring Boot 2.x / 3.x / 4.x** 多版本。

## Build Commands

```bash
# 编译整个项目
mvn compile

# 运行测试
mvn test

# 运行单个测试类
mvn test -Dtest=ClassName

# 编译特定模块（oss-core 使用 Java 8 编译）
mvn compile -Dmaven.compiler.source=1.8 -Dmaven.compiler.target=1.8 -pl oss-core

# 安装到本地仓库
mvn install

# 跳过测试
mvn install -DskipTests

# 执行集成测试（需要 MinIO 等）
mvn verify
```

## Module Structure

```
oss-spring-boot-starter/
├── oss-core/                   # 核心模块（Java 8 兼容，无 Spring 依赖）
├── oss-spring-boot2-starter/   # Spring Boot 2.x 适配（javax.servlet）
├── oss-spring-boot3-starter/   # Spring Boot 3.x 适配（jakarta.servlet）
├── oss-spring-boot4-starter/   # Spring Boot 4.x 适配（jakarta.servlet）
└── samples/                    # 示例项目
    ├── sample-springboot2/
    ├── sample-springboot3/
    ├── sample-springboot4/
    └── sample-frontend-web/    # 前端示例（Vue 3 + Vite + TypeScript）
```

## Architecture

**核心入口**: `OssTemplate`（门面模式）

| 方法 | 返回类型 | 功能 |
|------|----------|------|
| `put()` | `PutOperations` | 上传、复制、移动、分片上传 |
| `query()` | `QueryOperations` | 列举、树形、下载、预览 |
| `delete()` | `DeleteOperations` | 单个、批量、文件夹删除 |
| `unzip()` | `StreamUnzipOperations` | ZIP 流式解压 |
| `presign()` | `PresignOperations` | 预签名 URL |
| `tagging()` | `TaggingOperations` | 标签管理 |
| `bucket()` | `BucketOperations` | 版本控制、生命周期、CORS |

**基类**: `Operations` - 提供模板方法、策略模式、统一异常处理

**策略模式**: `DomainStrategyFactory` + `DomainStrategy` 实现类，处理不同 OSS 的域名格式：
- `VirtualHostedDomainStrategy` - 虚拟主机风格（阿里云 COS、华为云 OBS 等）
- `PathStyleDomainStrategy` - 路径风格（MinIO、通用 S3）

## Package Structure

核心包路径：`com.wiblog.oss`

```
com.wiblog.oss/
├── bean/           # 数据模型（ObjectInfo、OssProperties 等）
├── service/        # 核心服务（OssTemplate 及各 Operations）
│   └── strategy/   # 策略模式实现
├── resp/           # 响应封装
├── exception/      # 自定义异常
└── util/           # 工具类
```

## JDK 8 Compatibility

`oss-core` 模块必须保持 JDK 8 兼容，修改时禁止使用：
- `List.of()`, `Set.of()`, `Map.of()` → 使用 `Collections.unmodifiableList(Arrays.asList())`
- `instanceof Type var` 模式匹配 → 使用强制类型转换
- `InputStream.readAllBytes()` → 使用自定义工具方法
- `.toList()` → 使用 `.collect(Collectors.toList())`

## Spring Boot Version Support

| Starter | Spring Boot | Java | Servlet API |
|---------|-------------|------|-------------|
| `oss-spring-boot2-starter` | 2.3 ~ 2.7.x | 8+ | `javax.servlet` |
| `oss-spring-boot3-starter` | 3.0 ~ 3.x | 17+ | `jakarta.servlet` |
| `oss-spring-boot4-starter` | 4.0+ | 21+ | `jakarta.servlet` |

## Supported OSS Types

| type 值 | 存储服务 | 域名策略 |
|---------|----------|----------|
| `minio` | MinIO | 路径风格 |
| `cos` | 腾讯云 COS | 虚拟主机风格 |
| `obs` | 华为云 OBS | 虚拟主机风格 |
| `oss` | 阿里云 OSS | 虚拟主机风格 |
| `s3` | Amazon S3 | 虚拟主机风格 |

## Built-in REST API

配置 `oss.http.enable: true` 启用，提供完整的文件管理 REST 接口。所有路径以 `${oss.http.prefix}/oss` 为前缀。

## CI/CD

GitHub Actions 工作流：
- `ci.yml` - 单元测试与集成测试
- `deploy.yml` - 发布到 Maven Central
