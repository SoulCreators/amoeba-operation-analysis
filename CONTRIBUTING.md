# 开发协作规范

## 分支

- `main`：可发布代码。
- `codex/feature/<name>`：新功能。
- `codex/fix/<name>`：缺陷修复。
- `codex/refactor/<name>`：重构。

## 提交

提交格式：`type: 简短说明`。

示例：

```text
feat: 新增经营分析批次创建功能
fix: 修复基地权限越权查询问题
docs: 更新PVM计算规则
test: 新增事业部损益勾稽测试
```

## Pull Request

PR必须说明：变更背景、实现内容、测试结果、数据库变化、配置变化、回滚方式和风险。


## 工程边界与验收

- 目录和依赖变更必须通过 `make check`，参见 [目录 ADR](docs/architecture/decisions/0001-enterprise-repository-layout.md)。
- 后端模块测试与实现同目录；前端单测放 `frontend/tests/unit`，浏览器测试放 `frontend/e2e`。
- 财务规则变更必须说明公式、单位、零分母、精度、版本及权限影响，并给出财经批准的黄金样本；不能仅以页面截图验收。
- 已执行数据库迁移只新增不改写。变更需提交验证 SQL、数据保留说明和回滚/向前修复方案。
- 发布前执行 `make verify`，准备隔离 PostgreSQL；浏览器验证按 [开发说明](docs/development/local-development.md) 执行。
- 需求、ADR、接口、运行手册随代码更新。历史清单不充当当前实现证据。
