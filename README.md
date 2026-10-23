# 阿米巴经营分析平台

基地、产品线、事业部三视角经营分析平台。业务主线是 Excel 导入、校验与追溯、确定性损益计算、财经确认发布，以及 PVM、FC、指标树和 AI 草稿分析。

当前为 JDK 8 / Spring Boot 2.7.18 六模块单体，API 与 Worker 独立运行；前端使用 Vue 3 / TypeScript / Vite，数据库使用 PostgreSQL。已有功能及生产差距见 [代码分析与企业级优化报告](docs/reports/2026-10-21-enterprise-review.md)。目录整理不代表财务口径或上线验收完成。最新进展见 [第二阶段财经展示整改](docs/reports/2026-10-21-financial-display-remediation.md)。

## 工程导航

| 目录 | 职责 |
|---|---|
| [backend](backend/README.md) | common、domain、application、infrastructure、api、worker 六个 Maven 模块 |
| [frontend](frontend/README.md) | 应用入口、业务功能、共享 API 契约及前端测试 |
| [database](database/README.md) | 有序迁移、结构校验、手工回滚；SQL 保持原路径与编号 |
| [deploy](deploy/README.md) | 容器定义、环境辅助、构建脚本和可选 Maven 镜像 |
| [scripts](scripts/README.md) | 仓库级目录与依赖检查 |
| [templates](templates/README.md) | 已批准的导入模板，不放业务填报结果 |
| [tests](tests/README.md) | 跨模块测试材料与安全验证脚本 |
| [docs](docs/README.md) | 需求、项目治理、计划、架构、运维、历史资料和评审报告 |
| [demo](demo/index.html) | 独立演示原型，不作为正式计算结果 |

## 开发入口

1. 阅读 [需求与业务规则](docs/requirements/business-rules.md) 和 [V1.3 需求基线](docs/requirements/经营分析数据平台需求规格与详细设计_V1.3.md)。
2. 按 [本地开发与验证](docs/development/local-development.md) 准备 JDK 8、Maven、Node 和一次性测试数据库。
3. 执行 `make check` 检查工程边界；准备测试数据库后执行 `make verify`。
4. 按 [贡献规范](CONTRIBUTING.md) 在功能分支开发，通过 PR 评审。

API 唯一启动类：`com.sunwoda.evb.finance.analysis.api.AnalysisApiApplication`。Worker 启动类保留 `com.sunwoda.evb.finance.amoeba.worker.AnalysisWorkerApplication`。前端启动链：`main.ts → App.vue → features/workbench/WorkbenchPage.vue`。

## 财经与数据边界

正式数字来自可追溯事实和确定性规则；AI 不改账、不自动发布。未确认的 YU、Wh、FC、利润及风险口径必须明确标注阻断状态。权限取自服务端认证主体与授权范围；`local` Profile 的 `X-User-Id` 仅用于本地联调。

真实财务明细、凭据、导出文件和运行日志不得提交。`data/`、`artifacts/`、`.env.*` 等已加入忽略规则，忽略规则不等于数据脱敏或安全扫描。
