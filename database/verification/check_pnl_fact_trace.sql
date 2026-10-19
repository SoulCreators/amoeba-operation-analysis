-- V017 raw.pnl_fact_trace structure verification.
-- PostgreSQL executable; intentionally does not inspect table data.

DO $$
DECLARE
    v_table_oid oid;
    v_constraint_oid oid;
    v_index_oid oid;
BEGIN
    SELECT c.oid
      INTO v_table_oid
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'raw'
       AND c.relname = 'pnl_fact_trace'
       AND c.relkind IN ('r', 'p');

    IF v_table_oid IS NULL THEN
        RAISE EXCEPTION 'Missing table raw.pnl_fact_trace';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_attribute
         WHERE attrelid = v_table_oid
           AND attname = 'batch_id'
           AND NOT attisdropped
           AND attnum > 0
    ) THEN
        RAISE EXCEPTION 'Missing column raw.pnl_fact_trace.batch_id';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_attribute
         WHERE attrelid = v_table_oid
           AND attname = 'perspective'
           AND NOT attisdropped
           AND attnum > 0
    ) THEN
        RAISE EXCEPTION 'Missing column raw.pnl_fact_trace.perspective';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_attribute
         WHERE attrelid = v_table_oid
           AND attname = 'source_task_no'
           AND NOT attisdropped
           AND attnum > 0
    ) THEN
        RAISE EXCEPTION 'Missing column raw.pnl_fact_trace.source_task_no';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_attribute
         WHERE attrelid = v_table_oid
           AND attname = 'source_sheet_name'
           AND NOT attisdropped
           AND attnum > 0
    ) THEN
        RAISE EXCEPTION 'Missing column raw.pnl_fact_trace.source_sheet_name';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_attribute
         WHERE attrelid = v_table_oid
           AND attname = 'source_row_no'
           AND NOT attisdropped
           AND attnum > 0
    ) THEN
        RAISE EXCEPTION 'Missing column raw.pnl_fact_trace.source_row_no';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_attribute
         WHERE attrelid = v_table_oid
           AND attname = 'scope_code'
           AND NOT attisdropped
           AND attnum > 0
    ) THEN
        RAISE EXCEPTION 'Missing column raw.pnl_fact_trace.scope_code';
    END IF;

    SELECT con.oid
      INTO v_constraint_oid
      FROM pg_constraint con
     WHERE con.conrelid = v_table_oid
       AND con.conname = 'uk_pnl_fact_trace_task_sheet_row'
       AND con.contype = 'u';

    IF v_constraint_oid IS NULL THEN
        RAISE EXCEPTION 'Missing unique constraint raw.pnl_fact_trace.uk_pnl_fact_trace_task_sheet_row';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint con
          JOIN pg_class parent_table ON parent_table.oid = con.conrelid
          JOIN pg_namespace parent_schema ON parent_schema.oid = parent_table.relnamespace
          JOIN pg_class referenced_table ON referenced_table.oid = con.confrelid
          JOIN pg_namespace referenced_schema ON referenced_schema.oid = referenced_table.relnamespace
         WHERE con.conrelid = v_table_oid
           AND con.contype = 'f'
           AND parent_schema.nspname = 'raw'
           AND parent_table.relname = 'pnl_fact_trace'
           AND referenced_schema.nspname = 'meta'
           AND referenced_table.relname = 'analysis_batch'
           AND con.conkey = ARRAY[
               (SELECT attnum FROM pg_attribute
                 WHERE attrelid = v_table_oid AND attname = 'batch_id' AND NOT attisdropped)
           ]::smallint[]
           AND con.confkey = ARRAY[
               (SELECT attnum FROM pg_attribute
                 WHERE attrelid = referenced_table.oid AND attname = 'id' AND NOT attisdropped)
           ]::smallint[]
    ) THEN
        RAISE EXCEPTION 'Missing FK raw.pnl_fact_trace.batch_id -> meta.analysis_batch(id)';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint con
          JOIN pg_class parent_table ON parent_table.oid = con.conrelid
          JOIN pg_namespace parent_schema ON parent_schema.oid = parent_table.relnamespace
          JOIN pg_class referenced_table ON referenced_table.oid = con.confrelid
          JOIN pg_namespace referenced_schema ON referenced_schema.oid = referenced_table.relnamespace
         WHERE con.conrelid = v_table_oid
           AND con.contype = 'f'
           AND parent_schema.nspname = 'raw'
           AND parent_table.relname = 'pnl_fact_trace'
           AND referenced_schema.nspname = 'flow'
           AND referenced_table.relname = 'import_task'
           AND con.conkey = ARRAY[
               (SELECT attnum FROM pg_attribute
                 WHERE attrelid = v_table_oid AND attname = 'source_task_no' AND NOT attisdropped)
           ]::smallint[]
           AND con.confkey = ARRAY[
               (SELECT attnum FROM pg_attribute
                 WHERE attrelid = referenced_table.oid AND attname = 'task_no' AND NOT attisdropped)
           ]::smallint[]
    ) THEN
        RAISE EXCEPTION 'Missing FK raw.pnl_fact_trace.source_task_no -> flow.import_task(task_no)';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint con
         WHERE con.conrelid = v_table_oid
           AND con.contype = 'c'
           AND pg_get_constraintdef(con.oid) ~* 'source_row_no[[:space:]]*>=[[:space:]]*1'
    ) THEN
        RAISE EXCEPTION 'Missing CHECK constraint source_row_no >= 1 on raw.pnl_fact_trace';
    END IF;

    SELECT indexrelid
      INTO v_index_oid
      FROM pg_index i
      JOIN pg_class idx ON idx.oid = i.indexrelid
     WHERE i.indrelid = v_table_oid
       AND idx.relname = 'idx_pnl_fact_trace_batch_perspective_scope';

    IF v_index_oid IS NULL THEN
        RAISE EXCEPTION 'Missing index raw.pnl_fact_trace.idx_pnl_fact_trace_batch_perspective_scope';
    END IF;

    SELECT indexrelid
      INTO v_index_oid
      FROM pg_index i
      JOIN pg_class idx ON idx.oid = i.indexrelid
     WHERE i.indrelid = v_table_oid
       AND idx.relname = 'idx_pnl_fact_trace_task_row';

    IF v_index_oid IS NULL THEN
        RAISE EXCEPTION 'Missing index raw.pnl_fact_trace.idx_pnl_fact_trace_task_row';
    END IF;
END
$$;

SELECT 'V017 raw.pnl_fact_trace structure verification passed.' AS verification_result;
