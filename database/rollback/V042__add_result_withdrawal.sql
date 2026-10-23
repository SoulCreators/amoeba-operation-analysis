-- 回滚 V042：删除结果撤回列并恢复状态约束。
ALTER TABLE calc.pnl_result
    DROP COLUMN IF EXISTS withdrawn_by,
    DROP COLUMN IF EXISTS withdrawn_at,
    DROP COLUMN IF EXISTS withdraw_reason,
    DROP COLUMN IF EXISTS notification_targets;

ALTER TABLE flow.export_task
    DROP CONSTRAINT IF EXISTS ck_export_task_snapshot_status;
ALTER TABLE flow.export_task
    ADD CONSTRAINT ck_export_task_snapshot_status CHECK (
        result_status IS NULL OR result_status IN ('CALCULATED', 'CONFIRMED', 'PUBLISHED')
    );

ALTER TABLE calc.calculation_version
    DROP CONSTRAINT IF EXISTS ck_calculation_version_status;
ALTER TABLE calc.calculation_version
    ADD CONSTRAINT ck_calculation_version_status CHECK (
        status IN ('CALCULATED', 'CONFIRMED', 'PUBLISHED', 'SUPERSEDED')
    );
