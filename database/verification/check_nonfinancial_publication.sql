-- V040 flow.nonfinancial_publication 结构约束探针。
-- 检查表、字段与检查约束；探针数据置于事务中并最终回滚。
-- 执行方式：psql -v ON_ERROR_STOP=1 -f database/verification/check_nonfinancial_publication.sql

DO $$
DECLARE
    v_table oid;
    v_column_name text;
    v_columns text[] := ARRAY[
        'period', 'status', 'conflict_count', 'published_by', 'published_at'
    ];
    v_constraint_name text;
    v_constraints text[] := ARRAY[
        'ck_nonfinancial_publication_status',
        'ck_nonfinancial_publication_conflict_count'
    ];
BEGIN
    SELECT c.oid INTO v_table
      FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'flow' AND c.relname = 'nonfinancial_publication' AND c.relkind IN ('r', 'p');
    IF v_table IS NULL THEN
        RAISE EXCEPTION 'Missing table flow.nonfinancial_publication';
    END IF;

    FOREACH v_column_name IN ARRAY v_columns LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_attribute
             WHERE attrelid = v_table AND attname = v_column_name
               AND NOT attisdropped AND attnum > 0
        ) THEN
            RAISE EXCEPTION 'Missing column flow.nonfinancial_publication.%', v_column_name;
        END IF;
    END LOOP;

    FOREACH v_constraint_name IN ARRAY v_constraints LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
             WHERE conrelid = v_table AND conname = v_constraint_name
        ) THEN
            RAISE EXCEPTION 'Missing constraint %', v_constraint_name;
        END IF;
    END LOOP;
END
$$;

BEGIN;

DO $$
DECLARE
    v_period text := '2026-10';
    v_rejected boolean := false;
BEGIN
    INSERT INTO flow.nonfinancial_publication (period, status, conflict_count)
    VALUES (v_period, 'DRAFT', 0);

    -- 期间唯一。
    v_rejected := false;
    BEGIN
        INSERT INTO flow.nonfinancial_publication (period, status, conflict_count)
        VALUES (v_period, 'DRAFT', 0);
    EXCEPTION
        WHEN unique_violation THEN
            v_rejected := true;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected duplicate publication period to be rejected';
    END IF;

    -- 未知发布状态被拒绝。
    v_rejected := false;
    BEGIN
        UPDATE flow.nonfinancial_publication SET status = 'UNKNOWN' WHERE period = v_period;
    EXCEPTION
        WHEN check_violation THEN
            v_rejected := true;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected unknown publication status to be rejected';
    END IF;

    -- 负冲突数被拒绝。
    v_rejected := false;
    BEGIN
        UPDATE flow.nonfinancial_publication SET conflict_count = -1 WHERE period = v_period;
    EXCEPTION
        WHEN check_violation THEN
            v_rejected := true;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected negative conflict_count to be rejected';
    END IF;
END
$$;

ROLLBACK;

SELECT 'V040 nonfinancial publication verification passed.' AS verification_result;
