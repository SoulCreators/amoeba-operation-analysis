-- V022 PostgreSQL structure and behavior verification.
--
-- Run with V001-V022 applied:
--   psql -v ON_ERROR_STOP=1 -f database/verification/check_batch_import_constraints.sql
--
-- The behavior probe is enclosed in a transaction and the final ROLLBACK is
-- checked explicitly, so it leaves no rows in the business tables.

DO $$
DECLARE
    v_batch_table_oid oid;
    v_import_table_oid oid;
    v_column_name text;
    v_constraint_name text;
    v_constraint_definition text;
    v_constraint_validated boolean;
    v_required_batch_columns text[] := ARRAY[
        'id', 'batch_no', 'period', 'status', 'created_by', 'created_at',
        'updated_by', 'updated_at', 'perspective'
    ];
    v_required_import_columns text[] := ARRAY[
        'id', 'task_no', 'batch_id', 'dataset_code', 'file_name', 'object_key',
        'checksum', 'status', 'issue_count', 'created_by', 'created_at', 'updated_at'
    ];
BEGIN
    SELECT c.oid
      INTO v_batch_table_oid
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'meta'
       AND c.relname = 'analysis_batch'
       AND c.relkind IN ('r', 'p');

    IF v_batch_table_oid IS NULL THEN
        RAISE EXCEPTION 'Missing table meta.analysis_batch';
    END IF;

    SELECT c.oid
      INTO v_import_table_oid
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'flow'
       AND c.relname = 'import_task'
       AND c.relkind IN ('r', 'p');

    IF v_import_table_oid IS NULL THEN
        RAISE EXCEPTION 'Missing table flow.import_task';
    END IF;

    FOREACH v_column_name IN ARRAY v_required_batch_columns LOOP
        IF NOT EXISTS (
            SELECT 1
              FROM pg_attribute
             WHERE attrelid = v_batch_table_oid
               AND attname = v_column_name
               AND NOT attisdropped
               AND attnum > 0
        ) THEN
            RAISE EXCEPTION 'Missing column meta.analysis_batch.%', v_column_name;
        END IF;
    END LOOP;

    FOREACH v_column_name IN ARRAY v_required_import_columns LOOP
        IF NOT EXISTS (
            SELECT 1
              FROM pg_attribute
             WHERE attrelid = v_import_table_oid
               AND attname = v_column_name
               AND NOT attisdropped
               AND attnum > 0
        ) THEN
            RAISE EXCEPTION 'Missing column flow.import_task.%', v_column_name;
        END IF;
    END LOOP;

    FOREACH v_constraint_name IN ARRAY ARRAY[
        'ck_analysis_batch_status',
        'ck_import_task_status',
        'ck_import_task_issue_count_nonnegative',
        'ck_import_task_valid_issue_count'
    ] LOOP
        SELECT pg_get_constraintdef(con.oid, true), con.convalidated
          INTO v_constraint_definition, v_constraint_validated
          FROM pg_constraint con
         WHERE con.conrelid IN (v_batch_table_oid, v_import_table_oid)
           AND con.conname = v_constraint_name
           AND con.contype = 'c';

        IF v_constraint_definition IS NULL THEN
            RAISE EXCEPTION 'Missing CHECK constraint %', v_constraint_name;
        END IF;

        IF v_constraint_validated IS NOT TRUE THEN
            RAISE EXCEPTION 'CHECK constraint % is not validated', v_constraint_name;
        END IF;
    END LOOP;

    SELECT pg_get_constraintdef(con.oid, true)
      INTO v_constraint_definition
      FROM pg_constraint con
     WHERE con.conrelid = v_batch_table_oid
       AND con.conname = 'ck_analysis_batch_status'
       AND con.contype = 'c';
    IF v_constraint_definition NOT LIKE '%DRAFT%'
       OR v_constraint_definition NOT LIKE '%PUBLISHED%'
       OR v_constraint_definition NOT LIKE '%FAILED%' THEN
        RAISE EXCEPTION 'Batch status CHECK does not contain the expected enum values: %',
            v_constraint_definition;
    END IF;

    SELECT pg_get_constraintdef(con.oid, true)
      INTO v_constraint_definition
      FROM pg_constraint con
     WHERE con.conrelid = v_import_table_oid
       AND con.conname = 'ck_import_task_status'
       AND con.contype = 'c';
    IF v_constraint_definition NOT LIKE '%CREATED%'
       OR v_constraint_definition NOT LIKE '%VALIDATING%'
       OR v_constraint_definition NOT LIKE '%VALID%'
       OR v_constraint_definition NOT LIKE '%INVALID%'
       OR v_constraint_definition NOT LIKE '%CANCELLED%' THEN
        RAISE EXCEPTION 'Import status CHECK does not contain the expected enum values: %',
            v_constraint_definition;
    END IF;

    SELECT pg_get_constraintdef(con.oid, true)
      INTO v_constraint_definition
      FROM pg_constraint con
     WHERE con.conrelid = v_import_table_oid
       AND con.conname = 'ck_import_task_issue_count_nonnegative'
       AND con.contype = 'c';
    IF v_constraint_definition NOT LIKE '%issue_count >= 0%' THEN
        RAISE EXCEPTION 'Non-negative issue_count CHECK is incorrect: %',
            v_constraint_definition;
    END IF;

    SELECT pg_get_constraintdef(con.oid, true)
      INTO v_constraint_definition
      FROM pg_constraint con
     WHERE con.conrelid = v_import_table_oid
       AND con.conname = 'ck_import_task_valid_issue_count'
       AND con.contype = 'c';
    IF v_constraint_definition NOT LIKE '%VALID%'
       OR v_constraint_definition NOT LIKE '%issue_count = 0%' THEN
        RAISE EXCEPTION 'VALID issue_count CHECK is incorrect: %',
            v_constraint_definition;
    END IF;
END
$$;

DO $$
DECLARE
    v_batch_table_oid oid := 'meta.analysis_batch'::regclass;
    v_import_table_oid oid := 'flow.import_task'::regclass;
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_index i
          JOIN pg_class idx ON idx.oid = i.indexrelid
         WHERE i.indrelid = v_batch_table_oid
           AND i.indisunique
           AND pg_get_indexdef(i.indexrelid) ILIKE '%batch_no%'
    ) THEN
        RAISE EXCEPTION 'Existing unique batch_no index/constraint was not retained';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_class idx
          JOIN pg_namespace n ON n.oid = idx.relnamespace
          JOIN pg_index i ON i.indexrelid = idx.oid
         WHERE n.nspname = 'meta'
           AND idx.relname = 'idx_analysis_batch_period'
           AND i.indrelid = v_batch_table_oid
    ) THEN
        RAISE EXCEPTION 'Existing index meta.idx_analysis_batch_period was not retained';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_index i
          JOIN pg_class idx ON idx.oid = i.indexrelid
         WHERE i.indrelid = v_import_table_oid
           AND i.indisunique
           AND idx.relname = 'uk_import_task_batch_dataset'
    ) THEN
        RAISE EXCEPTION 'Existing idempotency unique index flow.uk_import_task_batch_dataset was not retained';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_class idx
          JOIN pg_namespace n ON n.oid = idx.relnamespace
          JOIN pg_index i ON i.indexrelid = idx.oid
         WHERE n.nspname = 'flow'
           AND idx.relname = 'idx_import_task_batch_status'
           AND i.indrelid = v_import_table_oid
    ) THEN
        RAISE EXCEPTION 'Existing index flow.idx_import_task_batch_status was not retained';
    END IF;
END
$$;

DROP TABLE IF EXISTS pg_temp.v022_constraint_verification_context;
CREATE TEMP TABLE v022_constraint_verification_context (
    suffix text NOT NULL
);

INSERT INTO v022_constraint_verification_context (suffix)
VALUES (substr(md5(clock_timestamp()::text || random()::text || pg_backend_pid()::text), 1, 16));

BEGIN;

DO $$
DECLARE
    v_suffix text;
    v_batch_no text;
    v_batch_id bigint;
    v_task_no text;
    v_rejected boolean;
BEGIN
    SELECT suffix INTO v_suffix FROM pg_temp.v022_constraint_verification_context;
    v_batch_no := 'v022_batch_' || v_suffix;

    INSERT INTO meta.analysis_batch (
        batch_no, period, status, created_by, updated_by
    ) VALUES (
        v_batch_no, '2026-01', 'DRAFT', 'v022-verification', 'v022-verification'
    ) RETURNING id INTO v_batch_id;

    v_rejected := FALSE;
    BEGIN
        INSERT INTO meta.analysis_batch (
            batch_no, period, status, created_by, updated_by
        ) VALUES (
            v_batch_no || '_BAD', '2026-01', 'CREATED', 'v022-verification', 'v022-verification'
        );
    EXCEPTION
        WHEN check_violation THEN
            v_rejected := TRUE;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected invalid meta.analysis_batch.status to be rejected';
    END IF;

    v_task_no := 'v022_task_' || v_suffix || '_CREATED';
    INSERT INTO flow.import_task (
        task_no, batch_id, dataset_code, file_name, status, issue_count, created_by
    ) VALUES (
        v_task_no, v_batch_id, 'V022_CREATED', 'v022-created.xlsx', 'CREATED', 0,
        'v022-verification'
    );

    v_rejected := FALSE;
    BEGIN
        INSERT INTO flow.import_task (
            task_no, batch_id, dataset_code, file_name, status, issue_count, created_by
        ) VALUES (
            'v022_task_' || v_suffix || '_STATUS_BAD', v_batch_id, 'V022_STATUS_BAD',
            'v022-status-bad.xlsx', 'DONE', 0, 'v022-verification'
        );
    EXCEPTION
        WHEN check_violation THEN
            v_rejected := TRUE;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected invalid flow.import_task.status to be rejected';
    END IF;

    v_rejected := FALSE;
    BEGIN
        INSERT INTO flow.import_task (
            task_no, batch_id, dataset_code, file_name, status, issue_count, created_by
        ) VALUES (
            'v022_task_' || v_suffix || '_NEGATIVE', v_batch_id, 'V022_NEGATIVE',
            'v022-negative.xlsx', 'INVALID', -1, 'v022-verification'
        );
    EXCEPTION
        WHEN check_violation THEN
            v_rejected := TRUE;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected negative flow.import_task.issue_count to be rejected';
    END IF;

    v_rejected := FALSE;
    BEGIN
        INSERT INTO flow.import_task (
            task_no, batch_id, dataset_code, file_name, status, issue_count, created_by
        ) VALUES (
            'v022_task_' || v_suffix || '_VALID_BAD', v_batch_id, 'V022_VALID_BAD',
            'v022-valid-bad.xlsx', 'VALID', 1, 'v022-verification'
        );
    EXCEPTION
        WHEN check_violation THEN
            v_rejected := TRUE;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected VALID flow.import_task with issue_count > 0 to be rejected';
    END IF;

    INSERT INTO flow.import_task (
        task_no, batch_id, dataset_code, file_name, status, issue_count, created_by
    ) VALUES (
        'v022_task_' || v_suffix || '_VALID', v_batch_id, 'V022_VALID',
        'v022-valid.xlsx', 'VALID', 0, 'v022-verification'
    );

    INSERT INTO flow.import_task (
        task_no, batch_id, dataset_code, file_name, status, issue_count, created_by
    ) VALUES (
        'v022_task_' || v_suffix || '_INVALID', v_batch_id, 'V022_INVALID',
        'v022-invalid.xlsx', 'INVALID', 2, 'v022-verification'
    );
END
$$;

ROLLBACK;

DO $$
DECLARE
    v_suffix text;
    v_rows bigint;
BEGIN
    SELECT suffix INTO v_suffix FROM pg_temp.v022_constraint_verification_context;

    SELECT COUNT(*) INTO v_rows
      FROM meta.analysis_batch
     WHERE batch_no LIKE 'v022_batch_' || v_suffix || '%';
    IF v_rows <> 0 THEN
        RAISE EXCEPTION 'Transaction probe left % meta.analysis_batch row(s)', v_rows;
    END IF;

    SELECT COUNT(*) INTO v_rows
      FROM flow.import_task
     WHERE task_no LIKE 'v022_task_' || v_suffix || '%';
    IF v_rows <> 0 THEN
        RAISE EXCEPTION 'Transaction probe left % flow.import_task row(s)', v_rows;
    END IF;
END
$$;

DROP TABLE pg_temp.v022_constraint_verification_context;

SELECT 'V022 batch/import status and issue-count constraint verification passed.'
    AS verification_result;
