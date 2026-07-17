# AGENTS.md

## 项目定位

这是一个基于 AWS S3 SDK v2 的对象存储 Spring Boot Starter，面向 S3 兼容对象存储提供 Java API、内置 HTTP 端点和 OpenAPI
注解元数据三类接入方式。

当前代码同时维护 Spring Boot 2、Spring Boot 3、Spring Boot 4 三套适配。修改时必须先确认变更属于核心能力、Web 适配、OpenAPI
元数据还是示例工程，避免跨模块误改。

测试支撑模块统一收敛在 `testing/` 目录下，但它们继续继承根工程 `pom.xml`。`testing/pom.xml` 只承担 Reactor 聚合职责，不承载额外依赖管理。

## 模块边界

| 模块                                 | 职责                                                        | 兼容基线                         |
|------------------------------------|-----------------------------------------------------------|------------------------------|
| `oss-domain`                       | 领域模型、异常类型、服务端口、域名策略                                       | Java 8                       |
| `oss-web-api`                      | Web 请求、响应、上传文件抽象和参数校验契约                                   | Java 8                       |
| `oss-core`                         | `OssTemplate`、AWS S3 客户端、核心操作实现                           | Java 8                       |
| `oss-spring-javax-web-support`     | Boot2 共用的 `javax` Web 控制器、异常处理、预览上下文和 OpenAPI 控制器支持实现     | Java 8，`javax.servlet`       |
| `oss-spring-jakarta-web-support`   | Boot3/4 共用的 `jakarta` Web 控制器、异常处理、预览上下文和 OpenAPI 控制器支持实现 | Java 17，`jakarta.servlet`    |
| `oss-spring-boot2-autoconfigure`   | Spring Boot 2 配置绑定与 `OssTemplate` 自动装配                    | Java 8，`javax.validation`    |
| `oss-spring-boot3-autoconfigure`   | Spring Boot 3 配置绑定与 `OssTemplate` 自动装配                    | Java 17，`jakarta.validation` |
| `oss-spring-boot4-autoconfigure`   | Spring Boot 4 配置绑定与 `OssTemplate` 自动装配                    | Java 21，`jakarta.validation` |
| `oss-spring-boot*-starter`         | 聚合基础 Java API 自动装配能力                                      | 对应 Spring Boot 版本            |
| `oss-spring-boot*-web-starter`     | 提供内置 REST 接口和统一异常处理                                       | 对应 Spring Boot 版本            |
| `oss-spring-boot*-openapi-starter` | 提供带 Swagger 注解元数据的 REST 控制器                               | 对应 Spring Boot 版本            |
| `testing`                          | 测试支撑聚合模块，仅负责归并测试模块目录结构                                    | `pom`                        |
| `oss-test-support`                 | 公共测试属性、控制器契约、自动配置契约和断言辅助                                  | Java 8                       |
| `oss-javax-test-support`           | Boot2 / `javax` 测试契约入口                                    | Java 8                       |
| `oss-jakarta-test-support`         | Boot3/4 / `jakarta` 测试契约入口                                | Java 17                      |
| `samples/sample-springboot*`       | 对应版本的后端示例                                                 | 对应 Spring Boot 版本            |
| `samples/sample-frontend-web`      | 前端演示工程                                                    | Vue 3、Vite、TypeScript        |

## 核心模型

| 对象                   | 角色                                     | 约束                                      |
|----------------------|----------------------------------------|-----------------------------------------|
| `OssClientOptions`   | 核心层内部客户端选项                             | 不依赖 Spring Boot 配置绑定模型                  |
| `OssProperties2/3/4` | 各 Spring Boot 版本的外部配置绑定对象              | 只在对应 autoconfigure 模块内使用                |
| `OssTemplate`        | 统一门面入口                                 | 构造时启动客户端，销毁时调用 `stop()`                 |
| `OssPutService`      | 上传、目录、服务端复制、移动、分片上传端口                 | 复制按对象大小自动选择 `CopyObject` 或 `UploadPartCopy` |
| `OssQueryService`    | 查询、树形列表、受限流式读取、下载、预览端口                 | 预览通过 `OssPreviewContext` 隔离 Servlet API |
| `OssDeleteService`   | 单对象、批量、目录删除端口                          | 失败路径必须显式处理                              |
| `OssUnzipService`    | ZIP 流式解压端口                             | 支持默认 Bucket、跨 Bucket 和条目前缀过滤            |
| `OssPresignService`  | GET/PUT 预签名 URL 端口                     | 过期时间使用 `Duration`                       |
| `OssTaggingService`  | 对象标签与 Bucket 标签端口                      | 覆盖、合并、删除语义需区分清楚                         |
| `OssBucketService`   | Bucket 版本控制、ACL、回滚、生命周期、CORS、策略、安全配置端口 | 部分能力取决于对象存储实现是否支持                       |

`ReadObjectRangeCommand` 使用 `offset + length` 表达单个字节区间。原始 Range 字符串重载仅用于兼容，新增代码优先使用类型化命令。

服务端复制仅适用于当前 `S3AsyncClient` 可同时访问的源和目标。对象不超过 5 GB 时使用单次复制，超过阈值时自动使用 multipart copy；分片大小复用 `partSizeInMb`，并发度不超过 `min(maxConnections, 8)`。失败时必须等待当前并发窗口收敛并中止未完成的 multipart upload。

## 自动配置规则

基础自动配置只在 `oss.enable=true` 时创建 `OssTemplate`。

Web 自动配置只在以下条件同时满足时注册 REST 控制器和异常处理器：

- `oss.enable=true`
- `oss.http.enable=true`
- 当前应用是 Web 应用
- 容器中不存在其他 `OssHttpEndpoint`

OpenAPI 自动配置会在普通 Web 自动配置之前执行，并通过 `OssHttpEndpoint` 抢占默认控制器装配。它只增加 Swagger 注解元数据，不内置
Swagger UI。

Spring Boot 2 使用 `META-INF/spring.factories` 注册自动配置。Spring Boot 3 和 Spring Boot 4 使用
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册自动配置。

## 域名策略

`DomainStrategyFactory` 当前按顺序选择域名策略：

| `oss.type` | URL 拼接策略                                           |
|------------|----------------------------------------------------|
| `cos`      | Virtual-Hosted，格式为 `{protocol}://{bucket}.{host}/` |
| `obs`      | Virtual-Hosted，格式为 `{protocol}://{bucket}.{host}/` |
| `minio`    | Path-Style，格式为 `{endpoint}/{bucket}/`              |
| 其他值或空值     | Path-Style 兜底                                      |

底层 S3 客户端目前仅在 `oss.type=minio` 时显式开启 `forcePathStyle`。调整其他厂商策略时，需要同时评估客户端寻址方式和对外
URL 拼接方式，不能只改其中一处。

## 构建命令

所有本地命令默认在 Windows PowerShell 中执行。

```powershell
# 编译整个项目
mvn compile

# 运行全部测试，提交前或大范围修改后再执行
mvn test

# 只测试指定模块及其依赖
mvn -pl oss-spring-boot3-web-starter -am test

# 只测试指定测试类
mvn -pl oss-spring-boot3-web-starter -Dtest=OssController3UnitTest test

# 安装到本地 Maven 仓库
mvn install

# 跳过测试安装
mvn install -DskipTests

# 发布构件打包验证
mvn -DskipTests -Prelease package
```

不要频繁执行全量测试。优先根据改动范围选择模块级测试，跨模块公共契约、自动配置、发布配置变更后再执行全量测试。

## 命令与编码约束

- 文件统一使用 UTF-8 编码，不能使用 BOM。
- 文档、注释和说明使用中文，专有名词除外。
- 本地命令必须按 Windows PowerShell 语义编写。
- 禁止使用 Windows 的 NUL 重定向写法。
- Windows PowerShell 没有 `/dev/null` 设备路径；本地需要丢弃输出时使用 `Out-Null` 或 `$null = ...`。只有在 GitHub Actions
  的 Linux shell 中，才允许使用 `>/dev/null`。
- 不要引入 Linux-only 语法作为默认本地命令。

## Java 兼容性

`oss-domain`、`oss-web-api`、`oss-core`、`oss-spring-javax-web-support`、`oss-test-support`、`oss-javax-test-support` 和
Spring Boot 2 相关模块必须保持 Java 8 兼容，禁止使用以下写法：

| 禁止写法                              | 替代方案                                                       |
|-----------------------------------|------------------------------------------------------------|
| `List.of()`、`Set.of()`、`Map.of()` | `Collections.unmodifiableList(Arrays.asList(...))` 或显式集合构造 |
| `instanceof Type value`           | 传统 `instanceof` 后强制类型转换                                    |
| `InputStream.readAllBytes()`      | 使用显式缓冲区读取                                                  |
| `stream().toList()`               | `stream().collect(Collectors.toList())`                    |

Spring Boot 3 模块以 Java 17 为基线。Spring Boot 4 模块以 Java 21 为基线。不要把高版本语言特性下沉到 Java 8 模块。

## 开发规则

- 先建模，再实现。改动前明确核心对象、状态、约束和失败路径。
- 命名使用对象存储领域语言，避免 `temp`、`foo`、无语义缩写等名称。
- 注释只解释设计原因、约束和权衡，不重复代码表面行为。
- 每个函数只表达一个抽象层次。
- 优先组合，不为了形式引入继承、设计模式或额外抽象。
- 所有失败路径必须显式处理，不能吞异常，不能静默失败。
- Web 层只做 HTTP 适配、参数转换和响应包装，核心逻辑必须留在 `oss-core` 或领域端口后面。
- Spring Boot 版本差异只能留在对应适配模块，不能泄漏到核心模块。

## REST 接口边界

内置 REST 接口由 `oss-spring-boot*-web-starter` 或 `oss-spring-boot*-openapi-starter` 提供，基础 Starter 不提供控制器。

控制器统一挂载在 `${oss.http.prefix:}/oss` 下。预览和下载接口直接写入响应流，不使用 `OssResponse` 包装；其他接口使用
`OssResponse` 返回。

Boot2 Web 适配使用 `javax.servlet`。Boot3 和 Boot4 Web 适配使用 `jakarta.servlet`。

## 验证策略

文档变更至少执行以下轻量验证：

```powershell
mvn -DskipTests compile
```

如果修改了自动配置、控制器或公共端口，至少执行受影响模块测试：

```powershell
mvn -pl oss-spring-boot3-web-starter -am test
```

如果修改了 Java 8 公共模块，必须确认 Java 8 兼容语法未被破坏，并优先执行：

```powershell
mvn -pl "oss-domain,oss-web-api,oss-core" -am test
```

集成测试依赖 MinIO。需要本地验证时，先启动 MinIO，再运行相关模块测试。不要把需要外部服务的验证写成无前置条件的默认步骤。

## 发布与持续集成

GitHub Actions 当前包含：

- `ci.yml`：使用 JDK 21，在 Linux 环境启动 MinIO，并执行 `mvn -B clean test`。
- `deploy.yml`：标签或手动触发发布，执行 release profile 并部署到 Maven Central。

CI 文件里的 shell 是 GitHub 托管环境语义，不代表本地默认命令语义。写用户文档和本地说明时仍以 Windows PowerShell 为准。
