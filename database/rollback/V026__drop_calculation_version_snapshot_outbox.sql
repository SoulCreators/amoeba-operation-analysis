-- V026 手工回滚：删除计算事件 Outbox、结果版本归属、结果版本表和计算快照表。
-- 本次修改：按外键依赖逆序回滚；执行前必须确认没有后续迁移或运行时数据依赖这些对象。

DROP INDEX IF EXISTS flow.idx_calculation_event_outbox_pending;
DROP INDEX IF EXISTS flow.idx_calculation_event_outbox_aggregate;
DROP TABLE IF EXISTS flow.calculation_event_outbox;

ALTER TABLE calc.pnl_result
    DROP CONSTRAINT IF EXISTS fk_pnl_result_calculation_version;
DROP INDEX IF EXISTS calc.uk_pnl_result_version_scope;
DROP INDEX IF EXISTS calc.uk_pnl_result_legacy_scope;
ALTER TABLE calc.pnl_result
    DROP COLUMN IF EXISTS calculation_version_id;

-- 本次修改：恢复 V007 的批次/视角/范围唯一约束，保证回滚后历史迁移语义完整。
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conrelid = 'calc.pnl_result'::regclass
           AND conname = 'uk_pnl_result'
    ) THEN
        ALTER TABLE calc.pnl_result
            ADD CONSTRAINT uk_pnl_result UNIQUE (batch_id, perspective, scope_code);
    END IF;
END
$$;

-- 本次修改：回滚时恢复 V009 的批次/版本唯一索引，保持旧版本迁移语义。
CREATE UNIQUE INDEX IF NOT EXISTS uk_pnl_result_version
    ON calc.pnl_result(batch_id, version_no);

DROP INDEX IF EXISTS calc.uk_calculation_version_current;
DROP INDEX IF EXISTS calc.idx_calculation_version_lookup;
DROP INDEX IF EXISTS calc.idx_calculation_version_job;
DROP TABLE IF EXISTS calc.calculation_version;

DROP INDEX IF EXISTS calc.idx_calculation_snapshot_hash;
DROP TABLE IF EXISTS calc.calculation_snapshot;
