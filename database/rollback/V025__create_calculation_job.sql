-- V025 手工回滚：删除计算作业表及活动 request_key 唯一索引。
-- 本次修改：回滚前必须确认没有 JDBC 适配器、调度器或后续迁移依赖该表；删除会清除表内作业记录。
DROP INDEX IF EXISTS flow.uk_calculation_job_active_request_key;
DROP TABLE IF EXISTS flow.calculation_job;
