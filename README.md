# 阿米巴经营分析平台｜项目开发仓库（JDK 8）

本目录是阿米巴经营分析平台的企业开发工作区，承接GitHub仓库源码、数据库脚本、部署文件、测试材料、交付文档及可交互业务验证Demo。

## 使用边界

- `backend/`、`frontend/`、`database/`、`deploy/`为可提交至GitHub的工程内容。
- `docs/`为与代码同步维护的架构、接口、计算规则、权限、AI和运维文档。
- `00_项目管理/`用于项目基线、任务和版本管理；不放真实财务数据。
- `tests/`只放脱敏样本、测试脚本和验收结果。
- 真实生产数据、账号密码、Token、AI密钥不得进入本目录或GitHub。

## 总体目录

```text
项目开发仓库_JDK8/
├── README.md
├── CONTRIBUTING.md
├── CHANGELOG.md
├── pom.xml                         # 后续克隆代码后生成/维护
├── .gitignore
├── .github/
│   ├── workflows/ci.yml
│   ├── pull_request_template.md
│   └── CODEOWNERS
├── 00_项目管理/
│   ├── 项目章程.md
│   ├── 版本与分支规范.md
│   ├── 需求开发测试追踪表.xlsx
│   └── 会议与决策记录/
├── backend/
│   ├── analysis-common/
│   ├── analysis-domain/
│   ├── analysis-application/
│   ├── analysis-infrastructure/
│   ├── analysis-api/
│   └── analysis-worker/
├── frontend/
├── demo/
│   └── index.html                   # 可直接双击打开的业务验证Demo
├── database/
│   ├── migration/
│   ├── seed/
│   ├── views/
│   ├── verification/
│   └── rollback/
├── templates/
│   ├── base/
│   ├── product-bu/
│   ├── common/
│   └── permission/
├── docs/
│   ├── architecture/
│   ├── api/
│   ├── database/
│   ├── calculation/
│   ├── permission/
│   ├── ai/
│   ├── testing/
│   └── operation/
├── deploy/
│   ├── docker/
│   ├── nginx/
│   ├── config/
│   ├── scripts/
│   └── monitoring/
└── tests/
    ├── api-integration/
    ├── calculation-golden-sample/
    ├── permission-matrix/
    ├── e2e/
    ├── performance/
    └── security/
```

## 当前实现基线

- JDK 8；默认Spring Boot 2.7.18；Maven构建。
- 前后端分离；后端为模块化单体，API和Worker分别部署。
- PostgreSQL保存配置、原始数据、标准数据、计算过程、结果、权限和审计数据。
- Excel原文件使用对象存储或公司文件服务保存。
- 一期采用人工导入、确定性规则计算、财经复核发布、本地AI辅助分析。
- ERP、预算、PMS等接口只预留标准数据入口，一期不直接接入。

## 业务验证Demo

直接打开 [demo/index.html](demo/index.html) 即可体验，无需启动后端或数据库。Demo覆盖：

`月度数据导入 → 基础配置导入 → 导入校验 → 未匹配处理 → 开始计算 → 三视角看板 → 分视角财经损益表 → 指标树/指标库 → 用户/菜单管理 → PVM → 权限切换 → AI分析草稿`。

Demo使用脱敏演示数据，仅用于财经和业务确认页面、操作流程、分析口径及权限边界，不代表正式生产数据。

## 开发提交规则

- `main`为受保护主分支，禁止直接提交。
- 功能开发使用`codex/feature/*`，缺陷修复使用`codex/fix/*`。
- 所有变更通过Pull Request合并。
- 提交信息采用Conventional Commits：`feat`、`fix`、`refactor`、`docs`、`test`、`chore`。
- 每个版本必须有变更记录、数据库迁移说明和回滚方案。
- 合并前至少通过JDK 8编译、单元测试、接口测试和必要的权限测试。

## 已完成的代码基线

首个基线已提交至`main`，包含：

1. JDK 8 Maven工程骨架；
2. 后端六个模块及API/Worker启动类；
3. Vue前端工程骨架；
4. 数据库迁移和种子数据目录；
5. CI、分支、提交、PR和代码所有者规范；
6. 部署配置模板及健康检查脚本。
