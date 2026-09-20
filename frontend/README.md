# 前端工程

Vue 3＋TypeScript应用，按工作台、数据导入、异常、配置、计算、三视角分析、AI、权限和审计页面组织。

## 当前联调工作台

`src/App.vue` 已提供一期可联调工作台：经营总览、财经损益结果、PVM、指标树和 AI 分析草稿五个入口，统一提供期间、视角、批次、范围筛选，并通过 `src/api.ts` 调用后端 `/api/v1` 接口。

- 请求自动携带 `X-User-Id`（本地切换用户仅用于联调；生产环境必须替换为统一认证会话）。
- 后端不可用时自动切换脱敏演示数据，不会把演示数据提交到后端。
- 可通过 `VITE_API_BASE_URL` 配置 API 网关地址，例如 `https://finance.example.com/api/v1`。
- 本地后端联调可启用 `local` Profile，使用 `X-User-Id` 注入演示主体；该 Profile 仅用于开发和验收，生产必须关闭并接入统一认证。

构建：

```bash
source ../deploy/scripts/env-local.sh
npm run build
```
