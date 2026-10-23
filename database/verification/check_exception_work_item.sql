-- V041 flow.exception_work_item 结构约束探针。
-- 检查表、字段与检查约束；探针数据置于事务中并最终回滚。
-- 执行方式：psql -v ON_ERROR_STOP=1 -f database/verification/check_exception_work_item.sql

DO $$
DECLARE
    v_table oid;
    v_column_name text;
    v_columns text[] := ARRAY[
        'id', 'task_no', 'object_code', 'issue_code', 'severity', 'status',
        'row_no', 'field_name', 'issue_message', 'raw_value',
        'ignored_by', 'ignored_at', 'ignore_reason'
    ];
    v_constraint_name text;
    v_constraints text[] := ARRAY[
        'ck_exception_work_item_severity',
        'ck_exception_work_item_status'
    ];
BEGIN
    SELECT c.oid INTO v_table
      FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'flow' AND c.relname = 'exception_work_item' AND c.relkind IN ('r', 'p');
    IF v_table IS NULL THEN
        RAISE EXCEPTION 'Missing table flow.exception_work_item';
    END IF;

    FOREACH v_column_name IN ARRAY v_columns LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_attribute
             WHERE attrelid = v_table AND attname = v_column_name
               AND NOT attisdropped AND attnum > 0
        ) THEN
            RAISE EXCEPTION 'Missing column flow.exception_work_item.%', v_column_name;
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
    v_rejected boolean := false;
BEGIN
    INSERT INTO flow.exception_work_item (task_no, object_code, issue_code, severity, status, issue_message)
    VALUES ('__probe__', 'OBJ', 'CODE', 'BLOCKING', 'OPEN', 'probe');

    -- 未知分级被拒绝。
    v_rejected := false;
    BEGIN
        UPDATE flow.exception_work_item SET severity = 'UNKNOWN' WHERE task_no = '__probe__';
    EXCEPTION
        WHEN check_violation THEN
            v_rejected := true;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected unknown severity to be rejected';
    END IF;

    -- 未知处理状态被拒绝。
    v_rejected := false;
    BEGIN
        UPDATE flow.exception_work_item SET status = 'RESOLVED' WHERE task_no = '__probe__';
    EXCEPTION
        WHEN check_violation THEN
            v_rejected := true;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected unknown status to be rejected';
    END IF;
END
$$;

ROLLBACK;

SELECT 'V041 exception work item verification passed.' AS verification_result;
