-- V043 calc.adjustment 结构与约束探针。
-- 检查表、字段与检查约束；探针数据置于事务中并最终回滚。
-- 执行方式：psql -v ON_ERROR_STOP=1 -f database/verification/check_adjustment.sql

DO $$
DECLARE
    v_column_name text;
    v_columns text[] := ARRAY[
        'adjustment_id', 'period', 'perspective', 'scope_code', 'scenario',
        'metric_code', 'amount', 'direction', 'reason', 'evidence_file_id',
        'expected_version', 'entered_by', 'status', 'entered_at',
        'voided_by', 'voided_at', 'void_reason'
    ];
    v_constraint_name text;
    v_constraints text[] := ARRAY[
        'ck_adjustment_scenario',
        'ck_adjustment_direction',
        'ck_adjustment_status',
        'ck_adjustment_amount_non_negative'
    ];
BEGIN
    IF to_regclass('calc.adjustment') IS NULL THEN
        RAISE EXCEPTION 'Missing table calc.adjustment';
    END IF;

    FOREACH v_column_name IN ARRAY v_columns LOOP
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
             WHERE table_schema = 'calc'
               AND table_name = 'adjustment'
               AND column_name = v_column_name
        ) THEN
            RAISE EXCEPTION 'Missing column calc.adjustment.%', v_column_name;
        END IF;
    END LOOP;

    FOREACH v_constraint_name IN ARRAY v_constraints LOOP
        IF NOT EXISTS (
            SELECT 1
              FROM pg_constraint c
              JOIN pg_class t ON t.oid = c.conrelid
             WHERE c.conname = v_constraint_name
               AND t.relname = 'adjustment'
        ) THEN
            RAISE EXCEPTION 'Missing constraint %', v_constraint_name;
        END IF;
    END LOOP;
END
$$;

BEGIN;

DO $$
DECLARE
    v_rejected boolean := false;
BEGIN
    INSERT INTO calc.adjustment (
        adjustment_id, period, perspective, scope_code, scenario,
        metric_code, amount, direction, reason, entered_by, status
    ) VALUES (
        '__adjustment_probe__', '2099-12', 'BASE', 'ALL', 'ACT',
        'REVENUE', 100.0, 'INCREASE', 'probe', 'v043-verification', 'ACTIVE'
    );

    -- 未知口径被拒绝。
    v_rejected := false;
    BEGIN
        UPDATE calc.adjustment SET scenario = 'FC' WHERE adjustment_id = '__adjustment_probe__';
    EXCEPTION
        WHEN check_violation THEN
            v_rejected := true;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected unknown scenario to be rejected';
    END IF;

    -- 未知方向被拒绝。
    v_rejected := false;
    BEGIN
        UPDATE calc.adjustment SET direction = 'SIDEWAYS' WHERE adjustment_id = '__adjustment_probe__';
    EXCEPTION
        WHEN check_violation THEN
            v_rejected := true;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected unknown direction to be rejected';
    END IF;

    -- 未知状态被拒绝。
    v_rejected := false;
    BEGIN
        UPDATE calc.adjustment SET status = 'ARCHIVED' WHERE adjustment_id = '__adjustment_probe__';
    EXCEPTION
        WHEN check_violation THEN
            v_rejected := true;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected unknown status to be rejected';
    END IF;

    -- 负金额被拒绝。
    v_rejected := false;
    BEGIN
        UPDATE calc.adjustment SET amount = -1 WHERE adjustment_id = '__adjustment_probe__';
    EXCEPTION
        WHEN check_violation THEN
            v_rejected := true;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected negative amount to be rejected';
    END IF;
END
$$;

ROLLBACK;

SELECT 'V043 adjustment verification passed.' AS verification_result;
