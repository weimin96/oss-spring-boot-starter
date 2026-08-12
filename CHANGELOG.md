# 更新日志

本项目所有重要的变更都将记录在此文件中。

## 未发布

## [v3.3.3] - 2026-08-12

## 修复

- 新增 `oss.connection-acquisition-timeout`，允许在 multipart 上传占满连接池时为后续对象操作配置有界等待时间，避免固定使用 SDK 默认值导致连接获取过早失败。

## [v3.3.2] - 2026-08-11

## 修复

- 修复已知长度的大对象携带预计算 SHA-256 时被 TransferManager 自动转为 multipart，导致部分 S3 兼容服务拒绝 `full-object` checksum 的问题。
- 大于 multipart 阈值或未知长度的上传不再发送预计算的完整对象 checksum；调用方仍可通过 metadata 保存业务 SHA-256。
- 已知长度且不超过 S3 单请求上限的 checksum 上传改用原生 `PutObject`，避免 TransferManager 在 S3 兼容服务上重新解释 checksum。

## [v3.3.1] - 2026-07-22

## 修复

- 修复异步上传被服务端拒绝并取消请求体订阅时，客户端取消异常掩盖服务端真实失败原因的问题。
- 上传失败现在优先保留服务端异常，并通过 suppressed exception 保留请求体写入失败。

## [v3.3.0] - 2026-07-18

## 特性

### 受限流式读取
- 新增 Java 8 兼容的 `ReadObjectRangeCommand` 和 `getInputStream(ReadObjectRangeCommand)` 接口。
- 使用 `offset + length` 表达单个对象字节区间，避免 Java API 直接暴露原始 HTTP Range 字符串。
- 在请求前校验负数、零长度和 `long` 溢出，调用方负责关闭返回的输入流。
- 对 Bucket 不存在、对象不存在、Range 越界、权限不足和其他读取失败提供明确领域错误码。
- 文件预览与 HTTP Range 下载链路复用类型化区间读取实现。
- `bucket` 为空时通过 `OssQueryService.getDefaultBucketName()` 解析默认 Bucket，统一内置实现与第三方适配器契约。
- 原始 Range 字符串重载保留用于兼容，并标记为过时。

### 服务端复制
- 对象不超过 5 GB 时使用单次 `CopyObject`，超过阈值时自动切换为 multipart upload 与 `UploadPartCopy`。
- 使用 `oss.part-size-in-mb` 作为基础分片大小，必要时自动增大，限制在 10,000 个分片内，并支持最大约 48.8 TiB 的 S3 对象。
- 分片复制按 `min(oss.max-connections, 8)` 受控并发提交，当前窗口结束后再进入下一批。
- 分片复制保留常用 HTTP 元数据、自定义 metadata 和对象标签。
- 复制时优先固定源对象版本，否则使用 ETag 条件，避免复制过程中源内容发生变化。
- 任一分片或完成阶段失败时自动中止 multipart upload，并保留中止失败的 suppressed exception。
- 同一并发窗口的多个分片失败会完整聚合；线程中断时先等待在途分片收敛，再恢复中断标记并执行 abort。
- 增加复制计划、完成结果和失败结果日志，记录策略、分片参数、并发度、耗时和错误码。
- `copyFile()` 和移动对象内部复制统一复用同一条单次/分片路由。
- `CopyObjectCommand` 支持指定 `sourceVersionId`，可把历史版本复制回同一对象 key。
- Bucket 时间回滚复用自动单次/分片复制链路，支持恢复超过 5 GB 的历史对象。

### 分片上传完整性
- `ChunkMerge` 新增必填的 `expectedPartCount` 和 `expectedSize`。
- 合并前回查服务端真实分片，校验连续编号、ETag、分片数量和字节总量。
- 分片上传校验 part number、uploadId、空内容及声明长度与实际字节数。

### 流式解压安全
- 拒绝绝对路径、Windows 盘符以及 `.`、`..` 路径段。
- 限制最多 10,000 个条目、单条目 5 GiB 和累计解压 50 GiB。

## 测试

- 增加默认 Bucket 类型化 Range 的 MinIO 集成测试。
- 增加由 `OSS_RUN_LARGE_COPY_TEST=true` 显式启用的 5 GB 以上服务端分片复制慢速测试。

## 修复

- 大对象移动与普通复制统一执行源版本或 ETag 一致性校验。
- 对象流式读取不再把 `NoSuchBucket` 错误误报为对象不存在。
- 分片复制使用 AWS SDK `Tagging` 模型传递标签，移除手工 URL 编码。
- 复制错误区分对象或 Bucket 不存在、权限不足、源对象变化和服务端复制能力不支持。
- `oss.part-size-in-mb` 增加 5120 MB 上限校验，并同步到 Boot 2/3/4 配置绑定。
- 移动源对象删除增加 ETag `If-Match` 条件，避免并发覆盖后误删新对象。
- 目录前缀不再通过扩展名推断，修复带点号目录扩大列表或删除范围的问题；空前缀递归删除被拒绝。
- `checkExist` 和删除前存在性检查仅把明确 404 视为不存在，不再隐藏权限、网络和服务端故障。
- 复制完成后的验证失败使用 `OBJECT_COPY_COMPLETED_VERIFY_FAILED`，明确目标可能已经写入。
- Bucket 回滚仅允许版本控制状态为 `Enabled`，版本状态查询不再折叠权限和服务端错误。
- 手工分片合并不再接受缺片、ETag 不一致或总大小错误的请求。
- 对象预览与下载在流式读取返回 `OBJECT_NOT_FOUND` 时恢复为 404 响应。

## [v3.2.0] - 2026-07-14

## 特性

### 对象命令 API
- 新增 Java 8 兼容的 `PutObjectCommand`、`CopyObjectCommand` 和 `StoredObject` 领域对象。
- 新增 `putObject(PutObjectCommand)`、`copyObject(CopyObjectCommand)` 和 `headObject(bucket, key)` 接口。
- 保留现有 `putObjectForKey()` 调用方式，并通过默认方法适配对象命令 API。
- 上传命令支持指定 Bucket、未知长度流、content type、metadata、tags、SHA-256 checksum 和 `createOnly`。

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
