-- V033 回滚脚本。
-- 本次新增：只删除投递尝试历史表及其索引，不删除 Outbox 事件正文和当前投递状态。

DROP INDEX IF EXISTS flow.uk_calculation_event_delivery_attempt_phase;
DROP INDEX IF EXISTS flow.idx_calculation_event_delivery_attempt_created;
DROP INDEX IF EXISTS flow.idx_calculation_event_delivery_attempt_event;
DROP TABLE IF EXISTS flow.calculation_event_delivery_attempt;
