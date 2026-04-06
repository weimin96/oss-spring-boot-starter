# 后端示例

该模块用于演示如何在独立 Spring Boot 应用中接入 `oss-spring-boot3-starter`。

## 启动方式

在仓库根目录执行：

```powershell
mvn -pl samples/sample-backend-springboot -am spring-boot:run
```

## 默认约定

- 默认使用本地 MinIO：`http://127.0.0.1:9000`
- 默认 Bucket：`oss-sample`
- 内置 REST 接口前缀：`/api/oss`

## 关键验证地址

- 应用状态：`http://127.0.0.1:8080/sample/status`
- 健康检查：`http://127.0.0.1:8080/actuator/health`
- OSS 连通性：`http://127.0.0.1:8080/api/oss/connect`
