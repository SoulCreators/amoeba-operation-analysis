-- 手工回滚 V018__create_export_task.sql。
-- 仅在确认没有依赖 flow.export_task 的后续对象或数据后执行。
DROP TABLE IF EXISTS flow.export_task;
