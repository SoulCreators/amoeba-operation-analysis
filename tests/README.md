# 测试分层与证据

| 位置 | 验证内容 |
|---|---|
| `backend/*/src/test/java` | 应用单测、HTTP 契约、权限、Spring 接线、PostgreSQL 事务与并发 |
| `frontend/tests/unit` | 接口契约、页面状态、配置导入、导出与字段保留 |
| `frontend/e2e` | 浏览器入口、空状态、上传与 API 不可用场景 |
| `database/verification` | 数据库结构、约束与原子性验证 |
| `tests/security` | JDBC 导出专项验证脚本 |
| `scripts/check-architecture.mjs` | 工程目录、依赖及迁移编号检查 |

执行前阅读 [开发环境说明](../docs/development/local-development.md)。数据库契约必须使用一次性测试库，测试数据只采用构造/脱敏样本。

本轮执行结果记录在 [评审报告](../docs/reports/2026-10-21-enterprise-review.md) 和 [机器可读摘要](../docs/reports/2026-10-21-validation.json)。测试通过不是已完成财经 UAT、渗透测试、灾备恢复或容量验证的证明。

第二阶段新增财经展示回归后的结果为前端 62 项、浏览器 4 项通过，见 [第二阶段报告](../docs/reports/2026-10-21-financial-display-remediation.md) 和 [验证摘要](../docs/reports/2026-10-21-phase2-validation.json)。第一阶段摘要为独立历史证据。
