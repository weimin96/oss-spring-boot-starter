# sample-frontend-web

基于 **Vite + Vue 3 + TypeScript + Tailwind CSS v4** 的 OSS 接口前端示例

---

## 技术栈

| 技术           | 版本   | 用途                       |
|--------------|------|--------------------------|
| Vite         | ^8   | 构建工具                     |
| Vue 3        | ^3.5 | 渲染框架（Composition API）    |
| TypeScript   | ^5   | 类型安全                     |
| Tailwind CSS | ^4   | 样式（@tailwindcss/vite 插件） |
| Vue Router   | ^4   | 客户端路由                    |
| Axios        | ^1   | HTTP 客户端                 |

---

## 快速启动

```powershell
# 1. 进入目录
cd samples/sample-frontend-web

# 2. 安装依赖
npm install

# 3. 配置后端地址
cp .env.example .env
# 编辑 .env，将 VITE_API_BASE_URL 改为实际后端地址

# 4. 启动开发服务器
npm run dev
```

浏览器访问 http://localhost:5173

---

## 环境变量

复制 `.env.example` 为 `.env`：

```env
# 后端 OSS 接口地址
VITE_API_BASE_URL=http://127.0.0.1:8080

# GitHub Pages 部署到仓库子路径时使用，日常本地开发通常保持默认 /
VITE_BASE_PATH=/
```

## GitHub Pages 发布

仓库新增了 [pages.yml](../../.github/workflows/pages.yml) 工作流，会在 `main` 分支更新前端 sample 或相关文档时自动构建并发布
`samples/sample-frontend-web`。

发布约束如下：

- 页面资源基路径由 `actions/configure-pages` 生成的 `BASE_URL` 注入 `Vite`，适配仓库子路径部署。
- 默认后端根地址来自仓库变量 `SAMPLE_FRONTEND_API_BASE_URL`。
- 如果仓库变量未配置，页面仍会正常打开，但请求会回退到同源 `/api/oss`。由于 GitHub Pages 只提供静态资源，通常需要在页面左下角手动输入演示后端根地址。

建议在仓库 `Settings -> Secrets and variables -> Actions -> Variables` 中新增：

```text
SAMPLE_FRONTEND_API_BASE_URL=https://你的演示后端域名
```

---

## 功能模块

| 路由                  | 视图        | 覆盖接口                                                                                                                                                                                                                                                           |
|---------------------|-----------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `/upload/simple`    | 单文件上传     | `POST /oss/object`、`POST /oss/folder`                                                                                                                                                                                                                          |
| `/upload/multipart` | 分片上传      | `POST /oss/multipart/{init,chunk,merge}`、`GET /oss/multipart/parts`                                                                                                                                                                                            |
| `/query`            | 文件查询      | `GET /oss/connect`、`GET /oss/object`、`GET /oss/object/exists`、`GET /oss/object/list`、`GET /oss/object/list/next-level`、`GET /oss/object/list/lazy`、`GET /oss/object/tree`、`GET /oss/object/tree/search`、`GET /oss/object/tree/folder`、`GET /oss/buckets`、预览/下载 |
| `/delete`           | 文件删除      | `DELETE /oss/object`、`DELETE /oss/objects`、`DELETE /oss/folder`                                                                                                                                                                                                |
| `/copy-move`        | 复制/移动     | `POST /oss/object/copy`、`POST /oss/object/move`                                                                                                                                                                                                                |
| `/unzip`            | 流式解压      | `POST /oss/unzip`、`POST /oss/unzip/cross-bucket`、`POST /oss/unzip/filter`                                                                                                                                                                                      |
| `/presign`          | 预签名 URL   | `GET /oss/presign/get`、`GET /oss/presign/put`（含浏览器直传演示）                                                                                                                                                                                                        |
| `/tag`              | 标签管理      | `GET/PUT/PATCH/DELETE /oss/object/tags`、`GET/PUT/DELETE /oss/bucket/tags`                                                                                                                                                                                      |
| `/bucket`           | Bucket 管理 | 创建、版本控制、生命周期、CORS、Policy、加密、公共访问屏蔽                                                                                                                                                                                                                             |

---
