-- V039 flow.nonfinancial_fact / flow.nonfinancial_result 结构约束探针。
-- 检查表、字段、检查约束与唯一索引语义；探针数据置于事务中并最终回滚。
-- 执行方式：psql -v ON_ERROR_STOP=1 -f database/verification/check_nonfinancial_metric.sql

DO $$
DECLARE
    v_fact oid;
    v_result oid;
    v_table oid;
    v_column_name text;
    v_columns text[];
    v_constraint_name text;
    v_constraints text[];
    v_index_name text;
    v_indexes text[];
BEGIN
    SELECT c.oid INTO v_fact
      FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'flow' AND c.relname = 'nonfinancial_fact' AND c.relkind IN ('r', 'p');
    IF v_fact IS NULL THEN
        RAISE EXCEPTION 'Missing table flow.nonfinancial_fact';
    END IF;

    SELECT c.oid INTO v_result
      FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'flow' AND c.relname = 'nonfinancial_result' AND c.relkind IN ('r', 'p');
    IF v_result IS NULL THEN
        RAISE EXCEPTION 'Missing table flow.nonfinancial_result';
    END IF;

    v_table := v_fact;
    v_columns := ARRAY[
        'id', 'period', 'base_code', 'production_line', 'cell_model', 'quality_category',
        'target_actual', 'quality_value', 'source_row_no', 'effective_file_version'
    ];
    FOREACH v_column_name IN ARRAY v_columns LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_attribute
             WHERE attrelid = v_table AND attname = v_column_name
               AND NOT attisdropped AND attnum > 0
        ) THEN
            RAISE EXCEPTION 'Missing column flow.nonfinancial_fact.%', v_column_name;
        END IF;
    END LOOP;

    v_table := v_result;
    v_columns := ARRAY[
        'id', 'period', 'base_code', 'production_line', 'cell_model', 'quality_category',
        'target_value', 'actual_value', 'gap', 'achievement_rate', 'pairing_status',
        'leader_employee_no', 'leader_conflict'
    ];
    FOREACH v_column_name IN ARRAY v_columns LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_attribute
             WHERE attrelid = v_table AND attname = v_column_name
               AND NOT attisdropped AND attnum > 0
        ) THEN
            RAISE EXCEPTION 'Missing column flow.nonfinancial_result.%', v_column_name;
        END IF;
    END LOOP;

    v_table := v_fact;
    v_constraints := ARRAY[
        'ck_nonfinancial_fact_target_actual',
        'ck_nonfinancial_fact_source_row_no'
    ];
    FOREACH v_constraint_name IN ARRAY v_constraints LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
             WHERE conrelid = v_table AND conname = v_constraint_name
        ) THEN
            RAISE EXCEPTION 'Missing constraint %', v_constraint_name;
        END IF;
    END LOOP;

    v_table := v_result;
    v_constraints := ARRAY['ck_nonfinancial_result_pairing_status'];
    FOREACH v_constraint_name IN ARRAY v_constraints LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
             WHERE conrelid = v_table AND conname = v_constraint_name
        ) THEN
            RAISE EXCEPTION 'Missing constraint %', v_constraint_name;
        END IF;
    END LOOP;

    -- 两个唯一键以 CREATE UNIQUE INDEX 落库，按 pg_class 校验（表达式索引）。
    v_indexes := ARRAY['uk_nonfinancial_fact_key', 'uk_nonfinancial_result_key'];
    FOREACH v_index_name IN ARRAY v_indexes LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_class c
             WHERE c.relname = v_index_name AND c.relkind = 'i'
        ) THEN
            RAISE EXCEPTION 'Missing index %', v_index_name;
        END IF;
    END LOOP;
END
$$;

BEGIN;

DO $$
DECLARE
    v_suffix text := substr(md5(clock_timestamp()::text || random()::text), 1, 16);
    v_period text := '2026-10';
    v_base text := 'BASE-' || v_suffix;
    v_model text := 'CELL-' || v_suffix;
    v_category text := 'YIELD';
    v_rejected boolean := false;
BEGIN
    -- 目标行 + 实际行可正常落库。
    INSERT INTO flow.nonfinancial_fact (
        period, base_code, production_line, cell_model, quality_category,
        target_actual, quality_value, source_row_no, effective_file_version
    ) VALUES (v_period, v_base, 'L1', v_model, v_category, 'TARGET', 0.9800000000, 1, 'fv-1');
    INSERT INTO flow.nonfinancial_fact (
        period, base_code, production_line, cell_model, quality_category,
        target_actual, quality_value, source_row_no, effective_file_version
    ) VALUES (v_period, v_base, 'L1', v_model, v_category, 'ACTUAL', 0.9750000000, 2, 'fv-1');

    -- 同业务键同目标/实际重复被拒绝（唯一索引兜底）。
    v_rejected := false;
    BEGIN
        INSERT INTO flow.nonfinancial_fact (
            period, base_code, production_line, cell_model, quality_category,
            target_actual, quality_value, source_row_no, effective_file_version
        ) VALUES (v_period, v_base, 'L1', v_model, v_category, 'TARGET', 0.9700000000, 3, 'fv-1');
    EXCEPTION
        WHEN unique_violation THEN
            v_rejected := true;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected duplicate fact business key to be rejected';
    END IF;

    -- 拉线可空：NULL 与空串在表达式索引下视为同一键，重复被拒绝。
    v_rejected := false;
    BEGIN
        INSERT INTO flow.nonfinancial_fact (
            period, base_code, production_line, cell_model, quality_category,
            target_actual, quality_value, source_row_no, effective_file_version
        ) VALUES (v_period, v_base, NULL, v_model, v_category, 'TARGET', 0.9700000000, 3, 'fv-1');
        INSERT INTO flow.nonfinancial_fact (
            period, base_code, production_line, cell_model, quality_category,
            target_actual, quality_value, source_row_no, effective_file_version
        ) VALUES (v_period, v_base, '', v_model, v_category, 'TARGET', 0.9700000000, 3, 'fv-1');
    EXCEPTION
        WHEN unique_violation THEN
            v_rejected := true;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected NULL and empty production_line to collide as one key';
    END IF;

    -- 未知目标/实际标识被拒绝。
    v_rejected := false;
    BEGIN
        INSERT INTO flow.nonfinancial_fact (
            period, base_code, production_line, cell_model, quality_category,
            target_actual, quality_value, source_row_no, effective_file_version
        ) VALUES (v_period, v_base, 'L1', v_model, v_category, 'UNKNOWN', 0.9700000000, 3, 'fv-1');
    EXCEPTION
        WHEN check_violation THEN
            v_rejected := true;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected unknown target_actual to be rejected';
    END IF;

    -- 负源行号被拒绝。
    v_rejected := false;
    BEGIN
        INSERT INTO flow.nonfinancial_fact (
            period, base_code, production_line, cell_model, quality_category,
            target_actual, quality_value, source_row_no, effective_file_version
        ) VALUES (v_period, v_base, 'L1', v_model, v_category, 'ACTUAL', 0.9700000000, -1, 'fv-1');
    EXCEPTION
        WHEN check_violation THEN
            v_rejected := true;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected negative source_row_no to be rejected';
    END IF;

    -- 配对结果：同业务键一条结果。
    INSERT INTO flow.nonfinancial_result (
        period, base_code, production_line, cell_model, quality_category,
        target_value, actual_value, gap, achievement_rate, pairing_status
    ) VALUES (v_period, v_base, 'L1', v_model, v_category,
        0.9800000000, 0.9750000000, -0.0050000000, 0.9948979592, 'BOTH');

    v_rejected := false;
    BEGIN
        INSERT INTO flow.nonfinancial_result (
            period, base_code, production_line, cell_model, quality_category,
            target_value, actual_value, gap, achievement_rate, pairing_status
        ) VALUES (v_period, v_base, 'L1', v_model, v_category,
            0.9800000000, 0.9750000000, -0.0050000000, 0.9948979592, 'BOTH');
    EXCEPTION
        WHEN unique_violation THEN
            v_rejected := true;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected duplicate result business key to be rejected';
    END IF;

    -- 未知配对状态被拒绝。
    v_rejected := false;
    BEGIN
        INSERT INTO flow.nonfinancial_result (
            period, base_code, production_line, cell_model, quality_category, pairing_status
        ) VALUES (v_period, v_base, 'L1', v_model, v_category, 'UNKNOWN');
    EXCEPTION
        WHEN check_violation THEN
            v_rejected := true;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected unknown pairing_status to be rejected';
    END IF;
END
$$;

ROLLBACK;

SELECT 'V039 nonfinancial metric verification passed.' AS verification_result;
