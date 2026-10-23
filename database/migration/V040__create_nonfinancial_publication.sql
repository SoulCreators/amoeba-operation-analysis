-- 非财经指标（电芯优率）发布记录：按期间唯一，记录发布状态与发布时冲突数快照。
-- 发布门禁由应用层在发布时重算冲突数并阻断冲突发布；本迁移只落结构与约束，不写业务数据。
CREATE TABLE IF NOT EXISTS flow.nonfinancial_publication (
    period VARCHAR(7) PRIMARY KEY,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    conflict_count INTEGER NOT NULL DEFAULT 0,
    published_by VARCHAR(64),
    published_at TIMESTAMP,
    CONSTRAINT ck_nonfinancial_publication_status
        CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_nonfinancial_publication_conflict_count CHECK (conflict_count >= 0)
);
