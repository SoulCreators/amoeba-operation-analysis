-- V032 回滚脚本。
-- 本次新增：只删除 Outbox 领取协调表及索引，不删除事件正文、投递结果或历史审计数据。

DROP INDEX IF EXISTS flow.idx_calculation_event_outbox_claim_expiry;
DROP TABLE IF EXISTS flow.calculation_event_outbox_claim;
