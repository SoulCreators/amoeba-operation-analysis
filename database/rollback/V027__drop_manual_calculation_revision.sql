-- V027 手工回滚：删除人工修订生命周期检索索引和修订关系表。
-- 本次修改：按 V027 对象依赖逆序回滚；不触碰 V026 版本、快照和 Outbox 对象。

DROP INDEX IF EXISTS flow.idx_calculation_event_outbox_manual_revision;
DROP INDEX IF EXISTS calc.idx_calculation_manual_revision_source;
DROP TABLE IF EXISTS calc.calculation_manual_revision;
