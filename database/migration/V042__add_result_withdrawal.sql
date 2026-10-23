-- 版本撤回：为计算结果与版本头补充撤回状态与撤回痕迹（人/时间/原因/通知对象）。
-- 金额与指标在撤回时保持不变，仅状态与撤回痕迹更新；本迁移只落结构与约束，不写业务数据。
ALTER TABLE calc.pnl_result
    ADD COLUMN IF NOT EXISTS withdrawn_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS withdrawn_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS withdraw_reason VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS notification_targets VARCHAR(1000);

ALTER TABLE flow.export_task
    DROP CONSTRAINT IF EXISTS ck_export_task_snapshot_status;
ALTER TABLE flow.export_task
    ADD CONSTRAINT ck_export_task_snapshot_status CHECK (
        result_status IS NULL OR result_status IN ('CALCULATED', 'CONFIRMED', 'PUBLISHED', 'WITHDRAWN')
    );

ALTER TABLE calc.calculation_version
    DROP CONSTRAINT IF EXISTS ck_calculation_version_status;
ALTER TABLE calc.calculation_version
    ADD CONSTRAINT ck_calculation_version_status CHECK (
        status IN ('CALCULATED', 'CONFIRMED', 'PUBLISHED', 'SUPERSEDED', 'WITHDRAWN')
    );
