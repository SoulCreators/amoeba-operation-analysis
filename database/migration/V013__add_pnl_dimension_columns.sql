-- 为基地×产品矩阵和线体专题保留原始分析维度。
-- 维度字段允许为空，以兼容一期旧模板；在对应数据源补齐前不参与正式指标发布。
ALTER TABLE raw.pnl_fact ADD COLUMN IF NOT EXISTS base_code VARCHAR(128);
ALTER TABLE raw.pnl_fact ADD COLUMN IF NOT EXISTS product_code VARCHAR(128);
ALTER TABLE raw.pnl_fact ADD COLUMN IF NOT EXISTS line_code VARCHAR(128);

ALTER TABLE raw.pnl_fact DROP CONSTRAINT IF EXISTS uk_pnl_fact_scope;
CREATE UNIQUE INDEX IF NOT EXISTS uk_pnl_fact_scope_dimensions
    ON raw.pnl_fact(batch_id, perspective, source_task_no, scope_code,
                    COALESCE(base_code, ''), COALESCE(product_code, ''), COALESCE(line_code, ''));

CREATE INDEX IF NOT EXISTS idx_pnl_fact_batch_dimensions
    ON raw.pnl_fact(batch_id, perspective, base_code, product_code, line_code);
