# 前端工程

Vue 3 / TypeScript / Vite 单页应用。现有视图由工作台编排，共享 API 访问层集中处理传输协议。

```text
src/
├── main.ts                        # 挂载唯一 App
├── App.vue                        # 应用组合入口
├── features/workbench/
│   ├── WorkbenchPage.vue          # 当前页面与用例编排
│   ├── analysis-presentation.ts  # 财经展示的状态、上下文和字段校验
│   ├── navigation.ts              # 视图类型与导航元数据
│   └── workbench.css              # 保留原全局样式语义
└── shared/api/
    ├── contracts.ts              # 请求、响应与状态类型
    ├── http.ts                   # Axios、响应解包、本地联调身份
    └── index.ts                  # 业务接口客户端
 tests/unit/                      # Vitest 组件和接口契约
 e2e/                             # Playwright 浏览器验证
```

从根目录执行 `make frontend`，或在本目录执行 `npm ci && npm run test:unit && npm run build`。本地联调使用 `npm run dev`，Vite 将 `/api` 转发到 `127.0.0.1:8080`；可通过 `VITE_API_BASE_URL` 指定 API 地址。

当前 CI 使用 Node 20，见 [开发环境说明](../docs/development/local-development.md)。`X-User-Id` 和用户切换只用于 `local` 后端，不能充当生产认证。

`shared/` 不得引用 `features/`。后续逐步将导入、配置、损益、分析、导出拆为独立 feature 和组合函数；本轮只建立边界并迁移现有实现，不宣称已完成全部组件拆分。FC 默认插值已移除；总览/矩阵仅提供 ACT/BGT，预测页校验已发布版本与完整字段。累计和自定义月份暂时禁用。当前结果与剩余边界见 [第二阶段报告](../docs/reports/2026-10-21-financial-display-remediation.md)，这不代表完整财务口径已验收。
