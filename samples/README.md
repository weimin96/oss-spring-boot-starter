# 示例目录

该目录存放 starter 的完整演示工程，包含后端示例和前端示例。

## 目录结构

| 目录                    | 说明                   | 技术栈                                         |
|-----------------------|----------------------|---------------------------------------------|
| `sample-springboot2`  | Spring Boot 2.x 后端示例 | Spring Boot 2.7.x + JDK 8+                  |
| `sample-springboot3`  | Spring Boot 3.x 后端示例 | Spring Boot 3.x + JDK 17+                   |
| `sample-springboot4`  | Spring Boot 4.x 后端示例 | Spring Boot 4.x + JDK 21+                   |
| `sample-frontend-web` | 前端演示示例               | Vue 3 + Vite + TypeScript + Tailwind CSS v4 |

## 快速运行

### 后端示例

每个后端示例都基于对应的 Spring Boot 版本构建，运行方式相同：

```bash
# 进入示例目录
cd samples/sample-springboot3

# 启动应用（默认使用 MinIO 作为存储后端）
# 确保本地运行有 MinIO 服务（localhost:9000）
mvn spring-boot:run
```

**默认配置**：示例工程已配置连接本地 MinIO（`localhost:9000`，账号 `minioadmin` / `minioadmin`），并自动创建名为 `oss-sample`
的测试 Bucket。

**启动 MinIO（Docker）**：

```bash
docker run -d \
  --name minio \
  -p 9000:9000 \
  -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  minio/minio server /data --console-address ":9001"
```

访问 MinIO 控制台：http://localhost:9001

### 前端示例

```cmd
# 进入前端目录
cd samples/sample-frontend-web

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端默认连接 `http://localhost:8080`（后端服务地址），如需修改可编辑 `.env` 文件。

如果需要把前端 sample 发布到 GitHub Pages，请推送 [pages.yml](../.github/workflows/pages.yml) 并在仓库变量中配置
`SAMPLE_FRONTEND_API_BASE_URL`。页面发布后也可以在左下角直接修改后端根地址，浏览器会把该值持久化到本地。

## 功能演示

示例工程演示了以下功能：

### 文件上传

- 单文件上传
- 创建文件夹
- 分片上传

### 文件查询

- 连接测试
- 文件详情
- 文件存在检查
- 列举对象
- 列举下一层级
- 懒加载分页
- 完整目录树
- 关键字搜索树
- 文件夹树
- 列举 Bucket
- 预览文件
- 下载文件
- 分片下载（HTTP Range）

### 文件删除

- 单个删除
- 批量删除
- 文件夹删除

### 复制 / 移动

- 复制文件
- 移动文件

### 流式解压

- ZIP 解压
- 跨 Bucket 解压
- 过滤解压

### 预签名 URL

- 下载预签名
- 上传预签名

### 标签管理

- 对象标签
- Bucket 标签

### Bucket 管理

- 创建 Bucket
- Bucket 详情
- Bucket ACL
- 版本控制
- Bucket 时间回滚
- 生命周期
- CORS 配置
- 访问策略
- 安全配置

## 注意事项

- 后端示例默认启用内置 REST API（`oss.http.enable: true`）
- 生产环境请根据实际情况调整配置
- 前端示例仅用于演示 OSS Starter 的 REST API 使用方式
