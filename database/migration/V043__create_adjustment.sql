-- V043 简易调整单底座。
-- 调整作为普通计算结果之上的独立计算层：金额与方向只作用于指定口径 + 报表项 + 范围，
-- 不修改源事实行，也不自动复制到其他数据集。已发布结果不受调整影响，调整生效需创建新计算版本。
-- 本迁移只新增结构与约束，不写业务数据。

CREATE TABLE IF NOT EXISTS calc.adjustment (
    adjustment_id VARCHAR(128) PRIMARY KEY,
    period VARCHAR(7) NOT NULL,
    perspective VARCHAR(32) NOT NULL,
    scope_code VARCHAR(128) NOT NULL,
    scenario VARCHAR(16) NOT NULL,
    metric_code VARCHAR(64) NOT NULL,
    amount NUMERIC(24, 8) NOT NULL,
    direction VARCHAR(16) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    evidence_file_id VARCHAR(128),
    expected_version VARCHAR(64),
    entered_by VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    entered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    voided_by VARCHAR(128),
    voided_at TIMESTAMP,
    void_reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_adjustment_scenario CHECK (scenario IN ('ACT', 'BGT')),
    CONSTRAINT ck_adjustment_direction CHECK (direction IN ('INCREASE', 'DECREASE')),
    CONSTRAINT ck_adjustment_status CHECK (status IN ('DRAFT', 'ACTIVE', 'VOID')),
    CONSTRAINT ck_adjustment_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT ck_adjustment_period CHECK (length(btrim(period)) > 0),
    CONSTRAINT ck_adjustment_reason CHECK (length(btrim(reason)) > 0)
);

-- 供普通计算叠加层按期间 + 视角 + 状态快速读取生效调整。
CREATE INDEX IF NOT EXISTS idx_adjustment_period_status
    ON calc.adjustment(period, perspective, status);
