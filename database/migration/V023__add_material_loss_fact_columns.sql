-- 仅为事实层保留收入成本模板中的实际/预算材料损耗，不并入既有正式结果。
ALTER TABLE raw.pnl_fact
    ADD COLUMN IF NOT EXISTS material_loss NUMERIC(24, 8),
    ADD COLUMN IF NOT EXISTS budget_material_loss NUMERIC(24, 8);
