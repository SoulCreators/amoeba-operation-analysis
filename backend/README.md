# 后端工程

模块化单体，API 和 Worker 分别部署。生产代码统一使用 `com.sunwoda.evb.finance.analysis`；现有 Worker 使用 `com.sunwoda.evb.finance.amoeba.worker`，为兼容启动和 Spring 显式接线保留。

| 模块 | 职责 | 直接内部依赖 |
|---|---|---|
| analysis-common | 通用响应、基础工具 | 无 |
| analysis-domain | 批次、事实、结果、版本、权限、状态策略及仓储契约 | common |
| analysis-application | 用例编排、权限、计算、导入、导出、版本与重试端口 | domain |
| analysis-infrastructure | JDBC、POI、文件系统、内存、AI 适配器 | application |
| analysis-api | HTTP 控制器、DTO、异常映射与 API 启动 | infrastructure |
| analysis-worker | 导出、清理、计算重试、Outbox 调度与 Worker 接线 | infrastructure |

构建依赖指向内层。基础设施实现内层端口，application 不依赖具体 JDBC/HTTP 实现。仓库检查见 `scripts/check-architecture.mjs`；它是静态门禁，不能替代 Spring 上下文和数据库事务测试。

## 构建和启动

在仓库根目录执行，详细环境见 [本地开发](../docs/development/local-development.md)：

```bash
source deploy/scripts/env-local.sh
mvn -B -Dmaven.repo.local="$MAVEN_REPO_LOCAL" verify
java -jar backend/analysis-api/target/analysis-api-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

`verify` 包含必须连接 PostgreSQL 的契约测试，需要先配置隔离测试数据库。`local` 仅供联调。`db` 启用 JDBC；`filesystem` 启用文件适配器。默认内存结果不可作为正式数据存储。

API 唯一启动类为 `com.sunwoda.evb.finance.analysis.api.AnalysisApiApplication`。旧 `amoeba.api`、common/domain/application/infrastructure 骨架已移除；历史内容可从 Git 基线 `c8ddc82` 追溯。

## 业务与实现导航

[系统架构和数据流](../docs/architecture/README.md) 解释各模块及计算链路；[代码评审报告](../docs/reports/2026-10-21-enterprise-review.md) 列出现有能力、源码证据和生产差距。API 的实际路由以 `analysis-api/.../controller` 为准，已有专题接口说明位于 [docs/api](../docs/api)。
