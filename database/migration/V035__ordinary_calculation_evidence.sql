-- Preserve every ordinary calculation's check evidence without rounding monetary differences.
ALTER TABLE calc.pnl_result_line ALTER COLUMN line_value TYPE NUMERIC;
CREATE TABLE calc.calculation_reconciliation (
    calculation_version_id VARCHAR(128) NOT NULL REFERENCES calc.calculation_version(calculation_version_id),
    scope_code VARCHAR(128) NOT NULL,
    check_code VARCHAR(64) NOT NULL,
    expected NUMERIC,
    actual NUMERIC,
    difference NUMERIC,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PASS', 'FAIL', 'BLOCKED')),
    reason TEXT,
    source_ids JSONB NOT NULL CHECK (jsonb_typeof(source_ids) = 'array'),
    PRIMARY KEY (calculation_version_id, scope_code, check_code),
    CHECK ((status = 'BLOCKED' AND expected IS NULL AND actual IS NULL AND difference IS NULL AND reason IS NOT NULL)
        OR (status IN ('PASS', 'FAIL') AND expected IS NOT NULL AND actual IS NOT NULL
            AND difference IS NOT NULL AND difference = actual - expected
            AND ((status = 'PASS' AND difference = 0) OR (status = 'FAIL' AND difference <> 0))))
);
-- Snapshots are append-only. Deletion remains available to a controlled retention workflow.
CREATE FUNCTION calc.reject_snapshot_update() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW IS DISTINCT FROM OLD THEN RAISE EXCEPTION 'calculation snapshot is immutable'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER calculation_snapshot_immutable BEFORE UPDATE ON calc.calculation_snapshot
FOR EACH ROW EXECUTE FUNCTION calc.reject_snapshot_update();

-- Lock provenance of ordinary versions while allowing the existing lifecycle/current-pointer transitions.
CREATE FUNCTION calc.guard_ordinary_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM calc.calculation_snapshot WHERE snapshot_id = OLD.snapshot_id AND schema_version = 'ORDINARY-1') THEN
        IF (to_jsonb(NEW) - ARRAY['status','current_flag','confirmed_by','confirmed_at','published_by','published_at'])
            IS DISTINCT FROM
           (to_jsonb(OLD) - ARRAY['status','current_flag','confirmed_by','confirmed_at','published_by','published_at']) THEN
            RAISE EXCEPTION 'ordinary calculation provenance is immutable';
        END IF;
        IF NEW.status = 'PUBLISHED' AND OLD.status <> 'PUBLISHED' THEN
            IF NOT EXISTS (SELECT 1 FROM calc.calculation_reconciliation WHERE calculation_version_id = NEW.calculation_version_id)
                OR EXISTS (SELECT 1 FROM calc.calculation_reconciliation WHERE calculation_version_id = NEW.calculation_version_id AND status <> 'PASS') THEN
                RAISE EXCEPTION 'ordinary calculation reconciliation gate is blocked';
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER ordinary_version_provenance BEFORE UPDATE ON calc.calculation_version
FOR EACH ROW EXECUTE FUNCTION calc.guard_ordinary_version();
CREATE TRIGGER calculation_reconciliation_immutable BEFORE UPDATE ON calc.calculation_reconciliation
FOR EACH ROW EXECUTE FUNCTION calc.reject_snapshot_update();
