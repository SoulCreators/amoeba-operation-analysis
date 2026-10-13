-- 为线体单位制造费用元/Wh保留实际与预算产出分母。
-- 旧模板缺失时保持 NULL，服务端不得把缺失分母当作 0 发布正式指标。
ALTER TABLE raw.pnl_fact ADD COLUMN IF NOT EXISTS output_wh NUMERIC(24, 8);
ALTER TABLE raw.pnl_fact ADD COLUMN IF NOT EXISTS budget_output_wh NUMERIC(24, 8);
