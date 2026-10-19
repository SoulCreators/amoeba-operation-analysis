# 浏览器级 E2E smoke

本目录是 `DEV-FE-E2E-001 + TEST-FE-E2E-001` 的最小浏览器验收切片，仅验证已有前端行为，不修改 Vue 业务组件、后端、权限或公式。

## 覆盖范围

- 使用唯一、未导入数据的本地批次，验证页面加载、`真实数据模式`、批次阻断/空态和 8 张指标卡保持 `—`，不展示演示金额。
- 中止 `/api/v1/**` 请求，验证页面显示真实接口不可用错误，并清空旧指标，不回退为演示数据。
- 不验证角色/数据范围、401/403、导出生成、真实文件上传、财经口径或计算公式。

## 本地运行

1. 启动后端 local profile（默认 `8080`，使用本地联调主体；该 profile 不代表生产认证）：

   ```bash
   source ../deploy/scripts/env-local.sh
   mvn -o -pl backend/analysis-api -am spring-boot:run \
     -Dspring-boot.run.profiles=local \
     -Dspring-boot.run.mainClass=com.sunwoda.evb.finance.analysis.api.AnalysisApiApplication
   ```

2. 在 `frontend/` 安装依赖和 Chromium（首次运行需要下载浏览器）：

   ```bash
   npm ci
   npx playwright install chromium
   ```

3. 运行 E2E。未设置 `E2E_BASE_URL` 时，Playwright 会自动启动 Vite `127.0.0.1:5173`；后端仍需单独启动：

   ```bash
   npm run test:e2e
   ```

   如果前端已由其他进程启动，可指定地址；配置不会写死生产地址：

   ```bash
   E2E_BASE_URL=http://127.0.0.1:5173 npm run test:e2e
   ```

CI 独立 job 会先构建并启动后端 local profile；未设置 `E2E_BASE_URL`，因此由 Playwright 管理 Vite，避免测试依赖一个未启动的前端地址。

## 阻断说明

若本机没有 Chromium、无法访问 Playwright 浏览器下载源，或后端 local profile 未启动，命令会失败；失败结果必须保留为失败，不得改写为通过。生产认证、角色/范围、导出下载和真实数据回归仍需后续专项验收。
