-- 一期业务基线：三套视角共用批次、指标、权限与结果生命周期，视角差异由数据集和计算规则承载。
ALTER TABLE meta.analysis_batch
    ADD COLUMN IF NOT EXISTS perspective VARCHAR(32) NOT NULL DEFAULT 'BASE';

CREATE INDEX IF NOT EXISTS idx_analysis_batch_perspective
    ON meta.analysis_batch(perspective, period);

-- 一个批次可以包含多类月度明细；预算和映射等基础配置不混入月度业务批次。
CREATE TABLE IF NOT EXISTS flow.batch_dataset (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES meta.analysis_batch(id),
    dataset_code VARCHAR(64) NOT NULL,
    required_flag BOOLEAN NOT NULL DEFAULT TRUE,
    completeness_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    active_file_id BIGINT,
    issue_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_batch_dataset UNIQUE (batch_id, dataset_code)
);

CREATE INDEX IF NOT EXISTS idx_batch_dataset_status
    ON flow.batch_dataset(batch_id, completeness_status);

-- 经营总览指标库：三个视角共用展示指标，适用视角和公式可配置。
CREATE TABLE IF NOT EXISTS cfg.metric_definition (
    id BIGSERIAL PRIMARY KEY,
    metric_code VARCHAR(64) NOT NULL UNIQUE,
    metric_name VARCHAR(128) NOT NULL,
    applicable_perspective VARCHAR(32) NOT NULL DEFAULT 'ALL',
    unit VARCHAR(32),
    formula_expression TEXT NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ai_summary_template TEXT,
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_metric_definition_scope
    ON cfg.metric_definition(applicable_perspective, enabled, display_order);

-- 经营总览统一结果；基地、产品线、事业部的专项结果可在后续表中按同一批次关联。
CREATE TABLE IF NOT EXISTS calc.overview_result (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES meta.analysis_batch(id),
    perspective VARCHAR(32) NOT NULL,
    scope_code VARCHAR(128) NOT NULL DEFAULT 'ALL',
    metric_code VARCHAR(64) NOT NULL REFERENCES cfg.metric_definition(metric_code),
    actual_value NUMERIC(24, 6),
    budget_value NUMERIC(24, 6),
    gap_value NUMERIC(24, 6),
    previous_value NUMERIC(24, 6),
    result_status VARCHAR(32) NOT NULL DEFAULT 'CALCULATED',
    ai_summary TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_overview_result UNIQUE (batch_id, perspective, scope_code, metric_code)
);

CREATE INDEX IF NOT EXISTS idx_overview_result_query
    ON calc.overview_result(batch_id, perspective, scope_code);

-- 权限按“角色+视角+范围”表达，范围码支持 ALL、基地编码、产品线编码和事业部编码。
CREATE TABLE IF NOT EXISTS iam.user_scope (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    perspective VARCHAR(32) NOT NULL,
    scope_code VARCHAR(128) NOT NULL DEFAULT 'ALL',
    can_view_detail BOOLEAN NOT NULL DEFAULT FALSE,
    can_export BOOLEAN NOT NULL DEFAULT FALSE,
    can_edit_config BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_scope UNIQUE (user_id, role_code, perspective, scope_code)
);

CREATE INDEX IF NOT EXISTS idx_user_scope_lookup
    ON iam.user_scope(user_id, perspective, enabled);
