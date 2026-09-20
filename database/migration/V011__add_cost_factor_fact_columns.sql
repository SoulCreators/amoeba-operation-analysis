ALTER TABLE raw.pnl_fact
    ADD COLUMN IF NOT EXISTS material_cost NUMERIC(24, 8),
    ADD COLUMN IF NOT EXISTS labor_cost NUMERIC(24, 8),
    ADD COLUMN IF NOT EXISTS outsourcing_cost NUMERIC(24, 8),
    ADD COLUMN IF NOT EXISTS variable_mfg_cost NUMERIC(24, 8),
    ADD COLUMN IF NOT EXISTS fixed_mfg_cost NUMERIC(24, 8),
    ADD COLUMN IF NOT EXISTS budget_material_cost NUMERIC(24, 8),
    ADD COLUMN IF NOT EXISTS budget_labor_cost NUMERIC(24, 8),
    ADD COLUMN IF NOT EXISTS budget_outsourcing_cost NUMERIC(24, 8),
    ADD COLUMN IF NOT EXISTS budget_variable_mfg_cost NUMERIC(24, 8),
    ADD COLUMN IF NOT EXISTS budget_fixed_mfg_cost NUMERIC(24, 8);
