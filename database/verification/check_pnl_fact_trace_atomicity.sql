-- Database-level SQL atomicity verification for raw.pnl_fact and raw.pnl_fact_trace.
--
-- This script is intentionally not a Spring application wiring test. It verifies
-- PostgreSQL transaction/subtransaction behavior directly at the database level.
-- Run with PostgreSQL 16, for example:
--   psql -v ON_ERROR_STOP=1 -f database/verification/check_pnl_fact_trace_atomicity.sql

DROP TABLE IF EXISTS pg_temp.pnl_fact_trace_atomicity_context;

CREATE TEMP TABLE pnl_fact_trace_atomicity_context (
    batch_no VARCHAR(64) NOT NULL,
    task_no VARCHAR(64) NOT NULL,
    batch_id BIGINT,
    scope_code VARCHAR(128) NOT NULL,
    source_sheet_name VARCHAR(255) NOT NULL,
    old_source_file_name VARCHAR(255) NOT NULL,
    new_source_file_name VARCHAR(255) NOT NULL,
    old_source_checksum VARCHAR(128) NOT NULL,
    new_source_checksum VARCHAR(128) NOT NULL,
    source_row_no INTEGER NOT NULL
);

-- The backend PID, microsecond timestamp, and random suffix make this test data
-- unique without relying on or changing any application/business sequence.
INSERT INTO pnl_fact_trace_atomicity_context (
    batch_no,
    task_no,
    scope_code,
    source_sheet_name,
    old_source_file_name,
    new_source_file_name,
    old_source_checksum,
    new_source_checksum,
    source_row_no
)
SELECT
    format(
        'pnl_atomicity_%s_%s_%s',
        to_char(clock_timestamp(), 'YYYYMMDDHH24MISSUS'),
        pg_backend_pid(),
        substr(md5(random()::TEXT), 1, 8)
    ),
    format(
        'pnl_atomicity_task_%s_%s_%s',
        to_char(clock_timestamp(), 'YYYYMMDDHH24MISSUS'),
        pg_backend_pid(),
        substr(md5(random()::TEXT), 1, 8)
    ),
    'PNL_ATOMICITY_SCOPE',
    'atomicity-test-sheet',
    'pnl_atomicity_old.xlsx',
    'pnl_atomicity_new.xlsx',
    'atomicity-old-checksum',
    'atomicity-new-checksum',
    1;

-- Keep setup, both replacement checks, and cleanup in one top-level transaction.
-- If any assertion fails, PostgreSQL rolls back the whole test data set when the
-- psql session exits with ON_ERROR_STOP=1.
BEGIN;

WITH inserted_batch AS (
    INSERT INTO meta.analysis_batch (
        batch_no,
        period,
        status,
        perspective,
        created_by,
        updated_by
    )
    SELECT
        batch_no,
        '2026-01',
        'DRAFT',
        'BASE',
        'pnl-atomicity-verification',
        'pnl-atomicity-verification'
    FROM pg_temp.pnl_fact_trace_atomicity_context
    RETURNING id, batch_no
)
UPDATE pg_temp.pnl_fact_trace_atomicity_context context
   SET batch_id = inserted_batch.id
  FROM inserted_batch
 WHERE context.batch_no = inserted_batch.batch_no;

INSERT INTO flow.import_task (
    task_no,
    batch_id,
    dataset_code,
    file_name,
    checksum,
    status,
    issue_count,
    created_by
)
SELECT
    task_no,
    batch_id,
    'PNL_ATOMICITY_TEST',
    old_source_file_name,
    old_source_checksum,
    'CREATED',
    0,
    'pnl-atomicity-verification'
FROM pg_temp.pnl_fact_trace_atomicity_context;

INSERT INTO raw.pnl_fact (
    batch_id,
    perspective,
    source_task_no,
    period,
    scope_code,
    volume,
    revenue
)
SELECT
    batch_id,
    'BASE',
    task_no,
    '2026-01',
    scope_code,
    111.00000000,
    222.00000000
FROM pg_temp.pnl_fact_trace_atomicity_context;

INSERT INTO raw.pnl_fact_trace (
    batch_id,
    perspective,
    source_task_no,
    source_file_name,
    source_checksum,
    source_sheet_name,
    source_row_no,
    scope_code
)
SELECT
    batch_id,
    'BASE',
    task_no,
    old_source_file_name,
    old_source_checksum,
    source_sheet_name,
    source_row_no,
    scope_code
FROM pg_temp.pnl_fact_trace_atomicity_context;

-- Failed replacement: the exception block is a PostgreSQL subtransaction. The
-- delete, new inserts, and the intentionally violating insert must all roll
-- back together when the unique constraint is violated.
DO $$
DECLARE
    v_batch_id BIGINT;
    v_task_no VARCHAR(64);
    v_scope_code VARCHAR(128);
    v_source_sheet_name VARCHAR(255);
    v_source_row_no INTEGER;
    v_constraint_name TEXT;
    v_failure_caught BOOLEAN := FALSE;
BEGIN
    SELECT batch_id, task_no, scope_code, source_sheet_name, source_row_no
      INTO v_batch_id, v_task_no, v_scope_code, v_source_sheet_name, v_source_row_no
      FROM pg_temp.pnl_fact_trace_atomicity_context;

    BEGIN
        DELETE FROM raw.pnl_fact_trace
         WHERE batch_id = v_batch_id
           AND perspective = 'BASE'
           AND source_task_no = v_task_no;

        DELETE FROM raw.pnl_fact
         WHERE batch_id = v_batch_id
           AND perspective = 'BASE'
           AND source_task_no = v_task_no;

        INSERT INTO raw.pnl_fact (
            batch_id,
            perspective,
            source_task_no,
            period,
            scope_code,
            volume,
            revenue
        )
        VALUES (
            v_batch_id,
            'BASE',
            v_task_no,
            '2026-01',
            v_scope_code,
            999.00000000,
            888.00000000
        );

        INSERT INTO raw.pnl_fact_trace (
            batch_id,
            perspective,
            source_task_no,
            source_file_name,
            source_checksum,
            source_sheet_name,
            source_row_no,
            scope_code
        )
        VALUES (
            v_batch_id,
            'BASE',
            v_task_no,
            'pnl_atomicity_new.xlsx',
            'atomicity-new-checksum',
            v_source_sheet_name,
            v_source_row_no,
            v_scope_code
        );

        -- This is the deliberate failure. It conflicts with the new trace row
        -- on uk_pnl_fact_trace_task_sheet_row.
        INSERT INTO raw.pnl_fact_trace (
            batch_id,
            perspective,
            source_task_no,
            source_file_name,
            source_checksum,
            source_sheet_name,
            source_row_no,
            scope_code
        )
        VALUES (
            v_batch_id,
            'BASE',
            v_task_no,
            'pnl_atomicity_duplicate.xlsx',
            'atomicity-duplicate-checksum',
            v_source_sheet_name,
            v_source_row_no,
            v_scope_code
        );
    EXCEPTION
        WHEN unique_violation THEN
            GET STACKED DIAGNOSTICS v_constraint_name = CONSTRAINT_NAME;
            IF v_constraint_name IS DISTINCT FROM 'uk_pnl_fact_trace_task_sheet_row' THEN
                RAISE EXCEPTION
                    'Expected raw.pnl_fact_trace unique constraint violation, got %',
                    COALESCE(v_constraint_name, '<unknown>');
            END IF;
            v_failure_caught := TRUE;
    END;

    IF NOT v_failure_caught THEN
        RAISE EXCEPTION
            'Expected deliberate raw.pnl_fact_trace unique constraint violation was not raised';
    END IF;
END
$$;

-- After the failed replacement, the old fact and old trace must still be the
-- only rows for this test task; neither replacement row may remain.
DO $$
DECLARE
    v_batch_id BIGINT;
    v_task_no VARCHAR(64);
    v_fact_count BIGINT;
    v_old_fact_count BIGINT;
    v_new_fact_count BIGINT;
    v_trace_count BIGINT;
    v_old_trace_count BIGINT;
    v_new_trace_count BIGINT;
BEGIN
    SELECT batch_id, task_no
      INTO v_batch_id, v_task_no
      FROM pg_temp.pnl_fact_trace_atomicity_context;

    SELECT COUNT(*)
      INTO v_fact_count
      FROM raw.pnl_fact
     WHERE batch_id = v_batch_id
       AND perspective = 'BASE'
       AND source_task_no = v_task_no;

    SELECT COUNT(*)
      INTO v_old_fact_count
      FROM raw.pnl_fact
     WHERE batch_id = v_batch_id
       AND perspective = 'BASE'
       AND source_task_no = v_task_no
       AND volume = 111.00000000
       AND revenue = 222.00000000;

    SELECT COUNT(*)
      INTO v_new_fact_count
      FROM raw.pnl_fact
     WHERE batch_id = v_batch_id
       AND perspective = 'BASE'
       AND source_task_no = v_task_no
       AND volume = 999.00000000
       AND revenue = 888.00000000;

    SELECT COUNT(*)
      INTO v_trace_count
      FROM raw.pnl_fact_trace
     WHERE batch_id = v_batch_id
       AND perspective = 'BASE'
       AND source_task_no = v_task_no;

    SELECT COUNT(*)
      INTO v_old_trace_count
      FROM raw.pnl_fact_trace
     WHERE batch_id = v_batch_id
       AND perspective = 'BASE'
       AND source_task_no = v_task_no
       AND source_file_name = 'pnl_atomicity_old.xlsx'
       AND source_checksum = 'atomicity-old-checksum';

    SELECT COUNT(*)
      INTO v_new_trace_count
      FROM raw.pnl_fact_trace
     WHERE batch_id = v_batch_id
       AND perspective = 'BASE'
       AND source_task_no = v_task_no
       AND source_file_name = 'pnl_atomicity_new.xlsx'
       AND source_checksum = 'atomicity-new-checksum';

    IF v_fact_count <> 1
       OR v_old_fact_count <> 1
       OR v_new_fact_count <> 0
       OR v_trace_count <> 1
       OR v_old_trace_count <> 1
       OR v_new_trace_count <> 0 THEN
        RAISE EXCEPTION
            'Failed replacement was not atomic: fact rows=% old=% new=%; trace rows=% old=% new=%',
            v_fact_count,
            v_old_fact_count,
            v_new_fact_count,
            v_trace_count,
            v_old_trace_count,
            v_new_trace_count;
    END IF;
END
$$;

-- Successful replacement transaction: old fact/trace are removed and exactly
-- one new fact/trace pair is committed for the same test batch and task.
DELETE FROM raw.pnl_fact_trace
 WHERE batch_id = (SELECT batch_id FROM pg_temp.pnl_fact_trace_atomicity_context)
   AND perspective = 'BASE'
   AND source_task_no = (SELECT task_no FROM pg_temp.pnl_fact_trace_atomicity_context);

DELETE FROM raw.pnl_fact
 WHERE batch_id = (SELECT batch_id FROM pg_temp.pnl_fact_trace_atomicity_context)
   AND perspective = 'BASE'
   AND source_task_no = (SELECT task_no FROM pg_temp.pnl_fact_trace_atomicity_context);

INSERT INTO raw.pnl_fact (
    batch_id,
    perspective,
    source_task_no,
    period,
    scope_code,
    volume,
    revenue
)
SELECT
    batch_id,
    'BASE',
    task_no,
    '2026-01',
    scope_code,
    999.00000000,
    888.00000000
FROM pg_temp.pnl_fact_trace_atomicity_context;

INSERT INTO raw.pnl_fact_trace (
    batch_id,
    perspective,
    source_task_no,
    source_file_name,
    source_checksum,
    source_sheet_name,
    source_row_no,
    scope_code
)
SELECT
    batch_id,
    'BASE',
    task_no,
    new_source_file_name,
    new_source_checksum,
    source_sheet_name,
    source_row_no,
    scope_code
FROM pg_temp.pnl_fact_trace_atomicity_context;

DO $$
DECLARE
    v_batch_id BIGINT;
    v_task_no VARCHAR(64);
    v_fact_count BIGINT;
    v_old_fact_count BIGINT;
    v_new_fact_count BIGINT;
    v_trace_count BIGINT;
    v_old_trace_count BIGINT;
    v_new_trace_count BIGINT;
BEGIN
    SELECT batch_id, task_no
      INTO v_batch_id, v_task_no
      FROM pg_temp.pnl_fact_trace_atomicity_context;

    SELECT COUNT(*)
      INTO v_fact_count
      FROM raw.pnl_fact
     WHERE batch_id = v_batch_id
       AND perspective = 'BASE'
       AND source_task_no = v_task_no;

    SELECT COUNT(*)
      INTO v_old_fact_count
      FROM raw.pnl_fact
     WHERE batch_id = v_batch_id
       AND perspective = 'BASE'
       AND source_task_no = v_task_no
       AND volume = 111.00000000
       AND revenue = 222.00000000;

    SELECT COUNT(*)
      INTO v_new_fact_count
      FROM raw.pnl_fact
     WHERE batch_id = v_batch_id
       AND perspective = 'BASE'
       AND source_task_no = v_task_no
       AND volume = 999.00000000
       AND revenue = 888.00000000;

    SELECT COUNT(*)
      INTO v_trace_count
      FROM raw.pnl_fact_trace
     WHERE batch_id = v_batch_id
       AND perspective = 'BASE'
       AND source_task_no = v_task_no;

    SELECT COUNT(*)
      INTO v_old_trace_count
      FROM raw.pnl_fact_trace
     WHERE batch_id = v_batch_id
       AND perspective = 'BASE'
       AND source_task_no = v_task_no
       AND source_file_name = 'pnl_atomicity_old.xlsx'
       AND source_checksum = 'atomicity-old-checksum';

    SELECT COUNT(*)
      INTO v_new_trace_count
      FROM raw.pnl_fact_trace
     WHERE batch_id = v_batch_id
       AND perspective = 'BASE'
       AND source_task_no = v_task_no
       AND source_file_name = 'pnl_atomicity_new.xlsx'
       AND source_checksum = 'atomicity-new-checksum';

    IF v_fact_count <> 1
       OR v_old_fact_count <> 0
       OR v_new_fact_count <> 1
       OR v_trace_count <> 1
       OR v_old_trace_count <> 0
       OR v_new_trace_count <> 1 THEN
        RAISE EXCEPTION
            'Successful replacement assertion failed: fact rows=% old=% new=%; trace rows=% old=% new=%',
            v_fact_count,
            v_old_fact_count,
            v_new_fact_count,
            v_trace_count,
            v_old_trace_count,
            v_new_trace_count;
    END IF;
END
$$;

-- Cleanup is part of the same top-level transaction, so a failed assertion
-- cannot leave test rows committed. Delete children before their referenced
-- import task and batch.
DELETE FROM raw.pnl_fact_trace
 WHERE batch_id = (SELECT batch_id FROM pg_temp.pnl_fact_trace_atomicity_context);

DELETE FROM raw.pnl_fact
 WHERE batch_id = (SELECT batch_id FROM pg_temp.pnl_fact_trace_atomicity_context);

DELETE FROM flow.import_task
 WHERE batch_id = (SELECT batch_id FROM pg_temp.pnl_fact_trace_atomicity_context)
   AND task_no = (SELECT task_no FROM pg_temp.pnl_fact_trace_atomicity_context);

DELETE FROM meta.analysis_batch
 WHERE id = (SELECT batch_id FROM pg_temp.pnl_fact_trace_atomicity_context)
   AND batch_no = (SELECT batch_no FROM pg_temp.pnl_fact_trace_atomicity_context);

COMMIT;

DROP TABLE pg_temp.pnl_fact_trace_atomicity_context;

SELECT 'raw.pnl_fact/raw.pnl_fact_trace database transaction atomicity verification passed.'
    AS verification_result;
