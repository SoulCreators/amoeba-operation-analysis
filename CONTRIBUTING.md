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

