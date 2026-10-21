-- V029 回滚脚本。
-- 本次新增：只删除重试领取协调表及其索引，不删除 V028 重试状态和人工审计数据。
DROP INDEX IF EXISTS flow.idx_calculation_retry_claim_expiry;
DROP TABLE IF EXISTS flow.calculation_retry_claim;
