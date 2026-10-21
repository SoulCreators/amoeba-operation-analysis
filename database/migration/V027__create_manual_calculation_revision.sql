-- V027 历史版本人工修订入口底座。
-- 本次修改：用独立关系表记录来源版本、目标版本、修订人、修订原因和修订时间，
-- 保证人工修订只能追加新的 calculation_version，不覆盖原历史版本内容。
-- 本迁移只新增 V027 对象，并复用 V026 已建立的版本头和生命周期 Outbox。

CREATE TABLE IF NOT EXISTS calc.calculation_manual_revision (
    revision_id VARCHAR(128) PRIMARY KEY,
    source_calculation_version_id VARCHAR(128) NOT NULL
        REFERENCES calc.calculation_version(calculation_version_id),
    target_calculation_version_id VARCHAR(128) NOT NULL
        REFERENCES calc.calculation_version(calculation_version_id),
    revised_by VARCHAR(128) NOT NULL,
    revision_reason TEXT NOT NULL,
    revised_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    trace_id VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_calculation_manual_revision_target
        UNIQUE (target_calculation_version_id),
    CONSTRAINT ck_calculation_manual_revision_distinct_versions CHECK (
        source_calculation_version_id <> target_calculation_version_id
    ),
    CONSTRAINT ck_calculation_manual_revision_revised_by CHECK (
        length(btrim(revised_by)) > 0
    ),
    CONSTRAINT ck_calculation_manual_revision_reason CHECK (
        length(btrim(revision_reason)) > 0
    )
);

-- 本次修改：支持按来源版本和修订时间审计人工修订链路，不改变 V026 原有索引。
CREATE INDEX IF NOT EXISTS idx_calculation_manual_revision_source
    ON calc.calculation_manual_revision(source_calculation_version_id, revised_at, revision_id);

-- 本次修改：为人工修订生命周期事件提供独立检索入口，事件正文继续复用 V026 Outbox。
CREATE INDEX IF NOT EXISTS idx_calculation_event_outbox_manual_revision
    ON flow.calculation_event_outbox(event_type, calculation_version_id, occurred_at, id)
    WHERE event_type = 'MANUAL_REVISED';
