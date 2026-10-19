# 部署目录

存放API、Worker、前端、Nginx、数据库迁移、环境变量模板、备份恢复和监控配置。

生产密钥只能通过公司密钥管理平台或环境变量注入，不得写入配置文件或提交到GitHub。

## 本地工具链

本项目支持用户级工具链，不要求管理员权限。准备好 `.toolchain/` 后，在项目根目录执行：

```bash
source deploy/scripts/env-local.sh
./deploy/scripts/build.sh
```

`.toolchain/` 仅用于本机，不提交到 Git；CI 使用工作流中声明的 JDK 8、Node.js 20。

## 本地 API 联调

```bash
source deploy/scripts/env-local.sh
mvn -pl backend/analysis-api -am spring-boot:run \
  -Dspring-boot.run.profiles=local
```

本地 Profile 会使用脱敏/内存适配器，并将 `X-User-Id` 转成本地 `Principal`，便于前端联调。它不是生产认证方案；生产环境必须关闭 `local`，由统一认证网关注入用户主体。

## Worker GOV-006 清理运行时配置

Worker 默认使用 `memory` profile，`db` 和 `filesystem` 不会自动启用。只有在数据库迁移已完成、文件根目录已挂载并明确激活两个 profile 后，才会运行 GOV-006 导出文件清理：

```bash
SPRING_PROFILES_ACTIVE=db,filesystem \
AMOEBA_DB_URL=jdbc:postgresql://db:5432/amoeba_analysis \
AMOEBA_DB_USERNAME=... \
AMOEBA_DB_PASSWORD=... \
ANALYSIS_STORAGE_ROOT=/var/lib/amoeba/exports \
java -jar analysis-worker.jar
```

Worker 运行时配置：

- `SPRING_PROFILES_ACTIVE`：必须显式设置为 `db,filesystem` 才启用清理接线；默认不启用。
- `AMOEBA_DB_URL`、`AMOEBA_DB_USERNAME`、`AMOEBA_DB_PASSWORD`：PostgreSQL datasource 配置。账号密码只能由密钥管理平台或运行时环境注入。
- `ANALYSIS_STORAGE_ROOT`：受控导出文件根目录，必须与挂载目录一致；Worker 不实现 S3/OSS。
- `ANALYSIS_WORKER_ID`、`ANALYSIS_WORKER_CLEANUP_BATCH_SIZE`：可选的 Worker 标识和单次清理批量上限。

只启用 `db` 或只启用 `filesystem` 都不会运行清理任务；两者同时启用时，`ExportCleanupWorker` 每十分钟执行一次有界清理。数据库不可用时，Worker 启动接线测试不代表数据库契约通过；请另行提供 `AMOEBA_DB_URL` 运行 PostgreSQL 契约测试。
Maven 依赖缓存也固定在 `.toolchain/m2`，避免修改用户目录。
