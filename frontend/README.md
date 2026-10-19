# 前端工程

Vue 3＋TypeScript应用，按工作台、数据导入、异常、配置、计算、三视角分析、AI、权限和审计页面组织。

## 当前联调工作台

`src/App.vue` 已提供一期可联调工作台：经营总览、财经损益结果、PVM、指标树、AI 分析草稿和配置中心入口，统一提供期间、视角、批次、范围筛选，并通过 `src/api.ts` 调用后端 `/api/v1` 接口。配置中心可维护指标库和映射关系，并查看批次数据质量摘要。

- 请求自动携带 `X-User-Id`（本地切换用户仅用于联调；生产环境必须替换为统一认证会话）。
- 页面只展示后端真实接口返回的数据；后端不可用或批次未完成计算时显示空状态和阻断提示，不会回退到脱敏演示数据。
- 可通过 `VITE_API_BASE_URL` 配置 API 网关地址，例如 `https://finance.example.com/api/v1`。
- 本地后端联调可启用 `local` Profile，使用 `X-User-Id` 注入本地联调主体；该 Profile 仅用于开发和验收，生产必须关闭并接入统一认证。

构建：

```bash
source ../deploy/scripts/env-local.sh
npm run build
```
