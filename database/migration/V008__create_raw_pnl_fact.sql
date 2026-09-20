-- Excel校验通过后，标准化事实层的持久化目标表。当前代码先使用文件适配器，数据库适配器按此结构接入。
CREATE TABLE IF NOT EXISTS raw.pnl_fact (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES meta.analysis_batch(id),
    perspective VARCHAR(32) NOT NULL,
    source_task_no VARCHAR(64) NOT NULL,
    period CHAR(7) NOT NULL,
    scope_code VARCHAR(128) NOT NULL,
    volume NUMERIC(24, 8),
    revenue NUMERIC(24, 8),
    sales_cost NUMERIC(24, 8),
    idle_expense NUMERIC(24, 8),
    base_expense NUMERIC(24, 8),
    rd_expense NUMERIC(24, 8),
    asset_impairment NUMERIC(24, 8),
    credit_impairment NUMERIC(24, 8),
    other_income NUMERIC(24, 8),
    budget_volume NUMERIC(24, 8),
    budget_revenue NUMERIC(24, 8),
    budget_sales_cost NUMERIC(24, 8),
    budget_idle_expense NUMERIC(24, 8),
    budget_base_expense NUMERIC(24, 8),
    budget_rd_expense NUMERIC(24, 8),
    budget_asset_impairment NUMERIC(24, 8),
    budget_credit_impairment NUMERIC(24, 8),
    budget_other_income NUMERIC(24, 8),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pnl_fact_scope UNIQUE (batch_id, perspective, source_task_no, scope_code)
);

CREATE INDEX IF NOT EXISTS idx_pnl_fact_batch_scope
    ON raw.pnl_fact(batch_id, perspective, scope_code);
