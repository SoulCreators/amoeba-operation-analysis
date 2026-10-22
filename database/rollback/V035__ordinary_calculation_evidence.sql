-- Only allowed before ordinary versions are created. Preserve evidence after use and roll forward instead.
BEGIN;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM calc.calculation_snapshot WHERE schema_version = 'ORDINARY-1') THEN
        RAISE EXCEPTION 'ordinary snapshots exist: retain evidence and use a forward fix';
    END IF;
END $$;
DROP TRIGGER ordinary_version_provenance ON calc.calculation_version;
DROP TRIGGER calculation_snapshot_immutable ON calc.calculation_snapshot;
DROP TRIGGER calculation_reconciliation_immutable ON calc.calculation_reconciliation;
DROP FUNCTION calc.guard_ordinary_version();
DROP FUNCTION calc.reject_snapshot_update();
DROP TABLE calc.calculation_reconciliation;
-- Keep line_value NUMERIC unrestricted: shrinking to NUMERIC(24,8) could destroy precision.
COMMIT;
