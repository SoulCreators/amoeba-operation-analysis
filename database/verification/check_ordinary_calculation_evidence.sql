-- V035 structural guardrails; behavioral rollback/concurrency evidence lives in PostgreSQL contract tests.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'calc'
        AND table_name = 'pnl_result_line' AND column_name = 'line_value' AND data_type = 'numeric' AND numeric_scale IS NULL) THEN
        RAISE EXCEPTION 'result values must retain unrestricted decimal precision';
    END IF;
    IF (SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal AND tgname IN
        ('calculation_snapshot_immutable', 'ordinary_version_provenance', 'calculation_reconciliation_immutable')) <> 3 THEN
        RAISE EXCEPTION 'ordinary calculation immutability triggers missing';
    END IF;
    IF to_regclass('calc.calculation_reconciliation') IS NULL THEN
        RAISE EXCEPTION 'reconciliation evidence table missing';
    END IF;
    IF EXISTS (SELECT 1 FROM calc.calculation_version v JOIN calc.calculation_snapshot s ON s.snapshot_id = v.snapshot_id
        WHERE s.schema_version = 'ORDINARY-1' AND v.status = 'PUBLISHED' AND
        (NOT EXISTS (SELECT 1 FROM calc.calculation_reconciliation c WHERE c.calculation_version_id = v.calculation_version_id)
         OR EXISTS (SELECT 1 FROM calc.calculation_reconciliation c WHERE c.calculation_version_id = v.calculation_version_id AND c.status <> 'PASS'))) THEN
        RAISE EXCEPTION 'published ordinary version has missing or failed reconciliation';
    END IF;
END $$;
