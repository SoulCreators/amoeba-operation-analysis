-- V028 回滚脚本。
-- 本次新增：只移除 V028 新增的人工动作表、索引和重试状态表，不删除共享 schema 或 V025/V026 对象。
DROP INDEX IF EXISTS flow.idx_calculation_retry_action_job;
DROP TABLE IF EXISTS flow.calculation_retry_action;
DROP INDEX IF EXISTS flow.idx_calculation_retry_due;
DROP TABLE IF EXISTS flow.calculation_retry;
