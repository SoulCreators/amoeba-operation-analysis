-- V021 immutable export input snapshot.  Legacy V018/V019 rows may have no
-- snapshot; new tasks created through the snapshot-aware port populate all
-- seven columns atomically and never update them on idempotent replay.
ALTER TABLE flow.export_task
    ADD COLUMN IF NOT EXISTS batch_no VARCHAR(255),
    ADD COLUMN IF NOT EXISTS result_version VARCHAR(255),
    ADD COLUMN IF NOT EXISTS perspective VARCHAR(32),
    ADD COLUMN IF NOT EXISTS result_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS format VARCHAR(16),
    ADD COLUMN IF NOT EXISTS summary_scopes TEXT[],
    ADD COLUMN IF NOT EXISTS detail_scopes TEXT[];

ALTER TABLE flow.export_task
    DROP CONSTRAINT IF EXISTS ck_export_task_snapshot_complete;

ALTER TABLE flow.export_task
    ADD CONSTRAINT ck_export_task_snapshot_complete CHECK (
        (batch_no IS NULL AND result_version IS NULL AND perspective IS NULL
            AND result_status IS NULL AND format IS NULL
            AND summary_scopes IS NULL AND detail_scopes IS NULL)
        OR
        (batch_no IS NOT NULL AND length(trim(batch_no)) > 0
            AND result_version IS NOT NULL AND length(trim(result_version)) > 0
            AND perspective IS NOT NULL
            AND result_status IS NOT NULL
            AND format IS NOT NULL
            AND summary_scopes IS NOT NULL
            AND detail_scopes IS NOT NULL)
    );

ALTER TABLE flow.export_task
    DROP CONSTRAINT IF EXISTS ck_export_task_snapshot_perspective,
    DROP CONSTRAINT IF EXISTS ck_export_task_snapshot_status,
    DROP CONSTRAINT IF EXISTS ck_export_task_snapshot_format;

ALTER TABLE flow.export_task
    ADD CONSTRAINT ck_export_task_snapshot_perspective CHECK (
        perspective IS NULL OR perspective IN ('BASE', 'PRODUCT_LINE', 'BUSINESS_UNIT')
    ),
    ADD CONSTRAINT ck_export_task_snapshot_status CHECK (
        result_status IS NULL OR result_status IN ('CALCULATED', 'CONFIRMED', 'PUBLISHED')
    ),
    ADD CONSTRAINT ck_export_task_snapshot_format CHECK (
        format IS NULL OR format IN ('CSV', 'XLSX')
    );
