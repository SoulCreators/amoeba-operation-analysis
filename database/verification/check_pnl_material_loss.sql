-- V023 raw.pnl_fact material-loss column verification.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'raw'
          AND table_name = 'pnl_fact'
          AND column_name = 'material_loss'
    ) THEN
        RAISE EXCEPTION 'Missing column raw.pnl_fact.material_loss';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'raw'
          AND table_name = 'pnl_fact'
          AND column_name = 'budget_material_loss'
    ) THEN
        RAISE EXCEPTION 'Missing column raw.pnl_fact.budget_material_loss';
    END IF;
END
$$;

SELECT 'V023 raw.pnl_fact material-loss columns verification passed.' AS verification_result;
