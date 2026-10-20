# 数据库脚本

本仓库当前没有引入 Flyway 或 Liquibase，也没有 Spring 自动执行迁移的配置。数据库结构由
`database/migration/V###__description.sql` 维护，按编号顺序由部署/运维流程使用 PostgreSQL
客户端执行；例如：

```bash
psql -v ON_ERROR_STOP=1 -f database/migration/V018__create_export_task.sql
```

数据库迁移必须使用递增编号，不允许修改已执行的迁移文件。每次结构变化必须同步补充校验脚本和回滚说明。

## V024 配置导入问题

`V024__create_configuration_import_issue.sql` 为 `flow.configuration_import_task`
建立独立的问题表和外键，不能复用月度事实导入的 `flow.import_issue`。
结构校验使用 `database/verification/check_configuration_import_issue.sql`；手工回滚使用
`database/rollback/V024__create_configuration_import_issue.sql`，执行前应完成备份。

## V018 导出任务元数据

`V018__create_export_task.sql` 新增 `flow.export_task`，覆盖当前
`ExportTaskPort` 能提供的任务 ID、用户、用户维度 `request_key` 幂等键、文件名/类型、状态以及
创建、更新、完成、过期和错误信息。`(user_id, request_key)` 是幂等唯一约束；`job_id` 另有唯一约束。

批次、结果版本、视角、格式范围快照等业务元数据尚未写入本表：当前应用端口尚未把这些字段传给
基础设施层，后续必须先扩展端口契约和结果版本快照，再新增迁移，不通过拼接 `request_key` 伪造这些字段。

表只保存最小任务元数据，不保存文件字节，也不声明对象存储已经存在；当前 JDBC 适配器的
`download()` 在文件存储适配器接入前保持不可用，不实现 Worker 或生产导出任务接线。

结构和幂等性校验：

```bash
psql -v ON_ERROR_STOP=1 -f database/verification/check_export_task.sql
```

手工回滚前应确认没有后续对象依赖该表，再执行 `database/rollback/V018__drop_export_task.sql`。
