-- 手工回滚 V020 前确认没有审计查询或后续对象依赖该表；此操作会删除审计记录。
DROP TABLE IF EXISTS audit.audit_event;
