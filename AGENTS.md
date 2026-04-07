# AGENTS.md

## Project Overview

`oss-spring-boot-starter` 是一个基于 Spring Boot 3.x 和 AWS SDK v2 的对象存储服务（OSS）工具库，兼容支持 S3 协议的主流云存储：腾讯云 COS、阿里云 OSS、华为云 OBS、七牛云、京东云、MinIO。

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
```

## Project Structure

```
oss-spring-boot-starter/
├── pom.xml                          # 父 POM，聚合工程
├── oss-spring-boot3-starter/        # 核心 starter 模块
│   ├── pom.xml
│   └── src/main/java/com/wiblog/oss/
├── samples/                         # 示例工程目录
│   ├── sample-backend-springboot/   # 后端示例（Spring Boot）
│   └── sample-frontend-web/          # 前端示例（Vue 3 + Vite）
```

## Architecture

### 核心模块 (oss-spring-boot3-starter)

```
com.wiblog.oss
├── service/
│   ├── OssTemplate.java              # 门面类，入口点
│   ├── PutOperations.java            # 文件上传（流式上传、multipart、拷贝、移动）
│   ├── QueryOperations.java          # 文件查询（列表、树形结构、下载）
│   ├── DeleteOperations.java         # 文件删除（单文件、批量、文件夹）
│   ├── PresignOperations.java        # 预签名 URL
│   ├── TaggingOperations.java        # 标签操作
│   ├── BucketOperations.java         # 存储桶操作（版本控制、生命周期、CORS）
│   └── StreamUnzipOperations.java    # 流式解压缩
├── config/
│   └── OssAutoConfiguration.java      # Spring Boot 自动配置
├── bean/
│   ├── OssProperties.java            # 配置属性类
│   ├── ObjectInfo.java              # 对象信息
│   └── ObjectTreeNode.java          # 树形节点
├── controller/
│   └── OssController.java            # HTTP REST 端点（可选启用）
├── strategy/                         # 域名访问策略模式
│   ├── DomainStrategy.java
│   ├── PathStyleDomainStrategy.java
│   └── VirtualHostedDomainStrategy.java
└── exception/
    └── OssException.java             # 自定义异常
```

### 设计要点

- **门面模式**：`OssTemplate` 是核心入口，通过 `put()`、`query()`、`delete()`、`presign()`、`tagging()`、`bucket()`、`unzip()` 方法返回具体操作类
- **生命周期管理**：`start()` 和 `stop()` 方法支持动态启停 OSS 客户端
- **策略模式**：`DomainStrategyFactory` 根据 OSS 类型选择合适的域名访问策略
- **自动配置**：通过 `@ConditionalOnProperty` 控制组件是否加载

## Configuration

在 `application.yml` 中配置：

```yaml
oss:
  enable: true
  endpoint: https://s3.ap-northeast-1.amazonaws.com
  access-key: YOUR_ACCESS_KEY
  secret-key: YOUR_SECRET_KEY
  bucket-name: your-bucket
  type: minio  # 支持: cos/oss/obs/minio/qiniu/jd
  auto-create-bucket: false
  throughput-in-gbps: 20.0
  part-size-in-mb: 10
  http:
    enable: true   # 启用内置 REST API 端点
    prefix: /api   # URL 前缀
```

## Sample Projects

### Backend Sample (sample-backend-springboot)

后端示例工程，展示如何在 Spring Boot 应用中集成和使用 `oss-spring-boot3-starter`。

```bash
cd samples/sample-backend-springboot
mvn spring-boot:run
```

### Frontend Sample (sample-frontend-web)

Vue 3 + TypeScript + Vite 构建的前端演示应用，提供可视化界面测试 OSS 操作。

```bash
cd samples/sample-frontend-web
pnpm install
pnpm dev
```

需要配置 `.env` 文件：
```bash
cp .env.example .env
# 编辑 .env，设置 VITE_API_BASE 为后端服务地址
```

## CI/CD

GitHub Actions 工作流位于 `.github/workflows/ci.yml`：

- 使用 MinIO 容器进行集成测试
- 测试覆盖通过 Coveralls 上报
- 主要分支：`main`、`spring3`

## Version Requirements

- JDK 21+
- Spring Boot 3.x
- Maven 3.9+