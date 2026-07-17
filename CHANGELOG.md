# 更新日志

本项目所有重要的变更都将记录在此文件中。

## [v3.2.0] - 2026-07-14

## 特性

### 对象命令 API
- 新增 Java 8 兼容的 `PutObjectCommand`、`CopyObjectCommand`、`ReadObjectRangeCommand` 和 `StoredObject` 领域对象。
- 新增 `putObject(PutObjectCommand)`、`copyObject(CopyObjectCommand)`、`getInputStream(ReadObjectRangeCommand)` 和 `headObject(bucket, key)` 接口。
- 保留现有 `putObjectForKey()`、`copyFile()` 和原始 Range 字符串调用方式，并通过默认方法适配对象命令 API。
- 上传命令支持指定 Bucket、未知长度流、content type、metadata、tags、SHA-256 checksum 和 `createOnly`。

### 受限流式读取
- 使用 `offset + length` 表达单个对象字节区间，避免 Java API 直接暴露原始 HTTP Range 字符串。
- 在请求前校验负数、零长度和 `long` 溢出，调用方负责关闭返回的输入流。
- 对对象不存在、Range 越界、权限不足和其他读取失败提供明确领域错误码。
- 文件预览与 HTTP Range 下载链路复用类型化区间读取实现。

### 服务端复制
- 对象不超过 5 GB 时使用单次 `CopyObject`，超过阈值时自动切换为 multipart upload 与 `UploadPartCopy`。
- 动态计算分片大小，限制在 10,000 个分片内，并支持最大约 48.8 TiB 的 S3 对象。
- 分片复制保留常用 HTTP 元数据、自定义 metadata 和对象标签。
- 复制时优先固定源对象版本，否则使用 ETag 条件，避免复制过程中源内容发生变化。
- 任一分片或完成阶段失败时自动中止 multipart upload，并保留中止失败的 suppressed exception。
- `copyFile()` 和移动对象内部复制统一复用同一条单次/分片路由。

### 客户端配置
- 将底层客户端从专用 CRT S3 客户端切换为标准 Java `S3AsyncClient`，并启用 Java multipart。
- 将连接超时和最大并发连接数映射到 Netty 异步 HTTP 客户端。
- 新增 API 调用总超时、单次尝试超时和 multipart 阈值配置，移除不再生效的 CRT 吞吐量配置。
- checksum 计算与验证固定为 `WHEN_REQUIRED`，兼容不要求 checksum 的 S3 实现。
- 关闭客户端时依次释放 Presigner、Transfer Manager 和 S3 client，单个资源关闭失败不再阻断后续资源释放。

### 对象移动
- 将对象移动调整为 staging-copy-delete 流程，最终对象校验成功且 staging 清理完成后才删除源对象。
- staging 和最终对象校验 size，并在源对象提供 SHA-256 checksum 时同步校验 checksum。
- 移动失败时保留源对象，staging 清理失败通过 suppressed exception 保留完整失败信息。

### 测试
- 补充多 Bucket、未知长度流、metadata、tags、checksum、并发同 key、`HeadObject` 和自动 multipart 的 MinIO 集成测试。
- 增加显式启用的 1 GB 以上 multipart 慢速测试。
- 补充 staging-copy-delete 成功、校验失败、复制失败和清理失败路径测试。

## [v3.1.2] - 2026-06-05

## 修复

### 对象 key
- 修复无扩展名对象 key 被误规范化为目录前缀的问题。

### MinIO
- 修复 MinIO 预签名 URL 未同步 path-style 配置的问题。
- 修复非 MinIO 类型下事件监听容器仍被注册的问题。

### 流式读写
- 修复输入流上传、对象下载和 ZIP 解压链路完整读入内存的问题。

### 模块边界
- 修复普通 Web Starter 传递 Swagger 注解依赖的问题。

## [v3.1.1] - 2026-06-04

## 修复

### 分片上传
- 修复 `listParts` 请求将 `max-parts` 设置为非法值导致 S3 兼容服务返回 400 的问题。
- 查询已上传分片时按 S3 单页上限 1000 分页读取，避免超过 1000 个分片时遗漏后续结果。

## [v3.1.0] - 2026-05-14

## 特性

### MinIO 对象事件监听
- 新增 `OssObjectEvent` 对象变化事件模型。
- 新增 `OssObjectEventListener` 业务监听端口。
- `oss.type=minio` 且 `oss.event.enable=true` 时支持自动启动对象事件监听。
- 支持监听对象创建和删除事件，并返回 Bucket、对象键、事件名、事件时间、大小、ETag、版本号和 sequencer。
- 监听实现不引入 MinIO SDK、消息队列或新增依赖。
- 支持 `oss.event.bucket-name`、`oss.event.events`、`oss.event.prefix`、`oss.event.suffix`、`oss.event.reconnect-interval` 配置。

## [v3.0.0] - 2026-04-18

## 特性

### 模块化拆分
- 基础 Java API：`oss-spring-boot2/3/4-starter`
- Web Starter（内置 REST 接口，不含 Swagger）：`oss-spring-boot2/3/4-web-starter`
- OpenAPI Starter（REST 接口含 Swagger 注解）：`oss-spring-boot2/3/4-openapi-starter`
- 测试支持模块：`oss-test-support`

### Spring Boot 多版本支持
- 新增 Spring Boot 4 支持
- Spring Boot 2 使用 `javax.servlet`
- Spring Boot 3 和 Spring Boot 4 使用 `jakarta.servlet`

### 文件操作能力
- 普通文件上传、文件夹上传
- 大文件分片上传、分片合并、查询已上传分片
- 大文件分片下载、普通下载，支持 HTTP Range
- 流式压缩下载 ZIP，支持文件夹下载场景
- 文件预览，支持 HTTP Range
- 文件复制、移动
- 文件删除、批量删除、文件夹递归删除
- 文件是否存在检查
- 对象元数据查询

### 文件查询能力
- 文件列表递归查询
- 下一层级文件列举
- 游标分页懒加载列表
- 目录树获取
- 目录树关键字搜索
- 仅目录树获取
- 连接测试

### 流式解压
- ZIP 流式解压
- 跨 Bucket 流式解压
- 按条目前缀过滤解压

### 文件夹压缩下载边界
- Java API 新增 `OssQueryService.writeFolderAsZip(...)`
- 内置 REST 接口新增 `GET /oss/folder/download?path=...&filename=...`
- `path` 语义为 S3 prefix，不依赖真实文件夹
- 目录占位对象不会写入 ZIP，真实对象按相对路径进入压缩包
- `path` 为空、prefix 下没有真实对象，或对象流打开失败时显式返回错误，不返回空 ZIP

### 示例工程
- 后端 sample 新增 `/api/files/folder/download` 用法示例
- 前端 sample 新增“文件夹压缩下载”页面

### 预签名 URL
- 生成下载预签名 URL
- 生成上传预签名 URL

### 标签管理
- 对象标签：获取、覆盖设置、合并更新、删除
- Bucket 标签：获取、覆盖设置、删除

### Bucket 管理
- Bucket 列举
- Bucket 详情聚合查询
- Bucket ACL 查询与设置
- 版本控制：查询、启用、挂起
- 时间回滚：按时间回滚 Bucket 可见状态
- 生命周期管理：查询、添加过期规则、删除规则
- CORS 配置：查询、设置允许所有来源、删除
- 策略管理：查询、设置、删除
- 加密：启用 SSE-S3 服务端加密
- 公共访问：开启公共访问屏蔽

### 存储类型支持
- MinIO
- 腾讯云 COS
- 华为云 OBS
- 通用 S3 兼容服务

## [v2.1.8]

## 特性

- 支持：腾讯云、阿里云、华为云、七牛云、京东云、MinIo
- 提供一系列的基础 web 端点和 swagger 文档，支持自由开启
- 查询
-
    - 连接测试
-
    - 存储桶查询
-
    - 大文件分片下载、普通下载
-
    - 文件列表懒加载查询、树形查询、模糊查询、层级查询
-
    - 文件夹列表查询
-
    - 文件预览、文本获取、文件详情
- 操作
-
    - 大文件分片上传、普通上传、
-
    - 创建文件夹、上传文件夹
-
    - 拷贝文件、移动文件
-
    - 校验是否存在
- 删除
-
    - 文件删除、文件夹删除
