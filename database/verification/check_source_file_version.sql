-- V038 flow.source_file_version 结构约束探针。
-- 本次修改：检查表、字段、约束、部分唯一索引语义与外键行为；探针数据置于事务中并最终回滚。
-- 执行方式：psql -v ON_ERROR_STOP=1 -f database/verification/check_source_file_version.sql

DO $$
DECLARE
    v_table oid;
    v_column_name text;
    v_columns text[] := ARRAY[
        'id', 'file_id', 'batch_no', 'period', 'dataset_code', 'object_code', 'version_no',
        'template_code', 'template_version', 'file_hash', 'object_key', 'row_count',
        'validation_status', 'active', 'uploaded_by', 'uploaded_at'
    ];
    v_constraint_name text;
    v_constraints text[] := ARRAY[
        'ck_source_file_version_row_count',
        'ck_source_file_version_validation_status',
        'fk_source_file_version_batch'
    ];
    v_index_name text;
    v_indexes text[] := ARRAY[
        'uk_source_file_version_object_version',
        'uk_source_file_version_active_object'
    ];
BEGIN
    SELECT c.oid INTO v_table
      FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'flow' AND c.relname = 'source_file_version' AND c.relkind IN ('r', 'p');

    IF v_table IS NULL THEN
        RAISE EXCEPTION 'Missing table flow.source_file_version';
    END IF;

    FOREACH v_column_name IN ARRAY v_columns LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_attribute
             WHERE attrelid = v_table AND attname = v_column_name
               AND NOT attisdropped AND attnum > 0
        ) THEN
            RAISE EXCEPTION 'Missing column flow.source_file_version.%', v_column_name;
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

    -- 两个唯一键以 CREATE UNIQUE INDEX 落库，属索引而非表约束，按 pg_class 校验。
    FOREACH v_index_name IN ARRAY v_indexes LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_class c
             WHERE c.relname = v_index_name AND c.relkind = 'i'
        ) THEN
            RAISE EXCEPTION 'Missing index %', v_index_name;
        END IF;
    END LOOP;

    -- uk_source_file_version_active_object 必须是部分唯一索引（同对象最多一个生效版本）。
    IF NOT EXISTS (
        SELECT 1 FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid
         WHERE c.relname = 'uk_source_file_version_active_object'
           AND i.indisunique AND i.indpred IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'uk_source_file_version_active_object must be a partial unique index';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = v_table AND conname = 'fk_source_file_version_batch' AND contype <> 'f'
    ) THEN
        RAISE EXCEPTION 'fk_source_file_version_batch must be a foreign key';
    END IF;
END
$$;

BEGIN;

DO $$
DECLARE
    v_suffix text := substr(md5(clock_timestamp()::text || random()::text), 1, 16);
    v_batch_no text := 'sfv-verification-' || v_suffix;
    v_object text := 'OBJ-' || v_suffix;
    v_period text := '2026-10';
    v_rejected boolean := false;
BEGIN
    INSERT INTO meta.analysis_batch (batch_no, period, status, created_by)
    VALUES (v_batch_no, v_period, 'DRAFT', 'verification');

    -- 第一个版本，先失效后生效可正常切换。
    INSERT INTO flow.source_file_version (
        file_id, batch_no, period, dataset_code, object_code, version_no,
        template_code, template_version, file_hash, object_key, row_count,
        validation_status, active, uploaded_by
    ) VALUES (
        v_suffix || '-f1', v_batch_no, v_period, 'DS-' || v_suffix, v_object, 1,
        'TPL-' || v_suffix, 'v1', 'hash-1', 'key-1', 10, 'VALID', FALSE, 'verification'
    );
    UPDATE flow.source_file_version SET active = TRUE WHERE file_id = v_suffix || '-f1';

    -- 第二个版本，切换生效指针：先失效旧版本，再生效新版本。
    INSERT INTO flow.source_file_version (
        file_id, batch_no, period, dataset_code, object_code, version_no,
        template_code, template_version, file_hash, object_key, row_count,
        validation_status, active, uploaded_by
    ) VALUES (
        v_suffix || '-f2', v_batch_no, v_period, 'DS-' || v_suffix, v_object, 2,
        'TPL-' || v_suffix, 'v2', 'hash-2', 'key-2', 20, 'VALID', FALSE, 'verification'
    );
    UPDATE flow.source_file_version SET active = FALSE
     WHERE period = v_period AND object_code = v_object AND active = TRUE;
    UPDATE flow.source_file_version SET active = TRUE WHERE file_id = v_suffix || '-f2';

    IF NOT EXISTS (
        SELECT 1 FROM flow.source_file_version
         WHERE period = v_period AND object_code = v_object AND active = TRUE AND file_id = v_suffix || '-f2'
    ) THEN
        RAISE EXCEPTION 'Expected active pointer to switch to the newest version';
    END IF;

    -- 同对象不得出现两个生效版本（部分唯一索引兜底）。
    v_rejected := false;
    BEGIN
        UPDATE flow.source_file_version SET active = TRUE WHERE file_id = v_suffix || '-f1';
    EXCEPTION
        WHEN unique_violation THEN
            v_rejected := true;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected two active versions for the same object to be rejected';
    END IF;

    -- 期间＋对象＋版本号唯一。
    v_rejected := false;
    BEGIN
        INSERT INTO flow.source_file_version (
            file_id, batch_no, period, dataset_code, object_code, version_no,
            template_code, file_hash, uploaded_by
        ) VALUES (
            v_suffix || '-dup', v_batch_no, v_period, 'DS-' || v_suffix, v_object, 2,
            'TPL-' || v_suffix, 'hash-dup', 'verification'
        );
    EXCEPTION
        WHEN unique_violation THEN
            v_rejected := true;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected duplicate (period, object, version_no) to be rejected';
    END IF;

    -- 负行数被拒绝。
    v_rejected := false;
    BEGIN
        UPDATE flow.source_file_version SET row_count = -1 WHERE file_id = v_suffix || '-f2';
    EXCEPTION
        WHEN check_violation THEN
            v_rejected := true;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected negative row_count to be rejected';
    END IF;

    -- 未知校验状态被拒绝。
    v_rejected := false;
    BEGIN
        UPDATE flow.source_file_version SET validation_status = 'UNKNOWN' WHERE file_id = v_suffix || '-f2';
    EXCEPTION
        WHEN check_violation THEN
            v_rejected := true;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected unknown validation_status to be rejected';
    END IF;

    -- 外键阻止引用不存在的批次。
    v_rejected := false;
    BEGIN
        INSERT INTO flow.source_file_version (
            file_id, batch_no, period, dataset_code, object_code, version_no,
            template_code, file_hash, uploaded_by
        ) VALUES (
            v_suffix || '-missing', v_suffix || '-missing-batch', v_period, 'DS-' || v_suffix,
            v_object, 3, 'TPL-' || v_suffix, 'hash-3', 'verification'
        );
    EXCEPTION
        WHEN foreign_key_violation THEN
            v_rejected := true;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected source file version referencing a missing batch to be rejected';
    END IF;
END
$$;

ROLLBACK;

SELECT 'V038 flow.source_file_version verification passed.' AS verification_result;
