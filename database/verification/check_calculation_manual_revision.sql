-- V027 历史版本人工修订结构和行为验证。
-- 本次修改：结构探针与最小行为探针均放在事务中，脚本结束只回滚临时数据。
-- 执行前提：V026 已完成，且数据库中存在 meta、flow、calc 三个基础 schema。

DO $$
BEGIN
    IF to_regclass('calc.calculation_manual_revision') IS NULL THEN
        RAISE EXCEPTION 'Missing V027 table calc.calculation_manual_revision';
    END IF;

    IF to_regclass('flow.idx_calculation_event_outbox_manual_revision') IS NULL THEN
        RAISE EXCEPTION 'Missing V027 index idx_calculation_event_outbox_manual_revision';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conrelid = 'calc.calculation_manual_revision'::regclass
           AND conname = 'uk_calculation_manual_revision_target'
    ) THEN
        RAISE EXCEPTION 'Missing V027 target uniqueness constraint';
    END IF;
END
$$;

BEGIN;

DO $$
DECLARE
    v_suffix text := substr(md5(clock_timestamp()::text || random()::text), 1, 16);
    v_batch_id bigint;
    v_batch_no text;
    v_job_id text;
    v_snapshot_id text;
    v_source_version_id text;
    v_target_version_id text;
    v_blank_target_version_id text;
    v_revision_id text;
    v_duplicate_rejected boolean := false;
    v_blank_reason_rejected boolean := false;
    v_source_current boolean;
    v_target_current boolean;
    v_event_count integer;
BEGIN
    v_batch_no := 'v027-verification-batch-' || v_suffix;
    v_job_id := 'v027-verification-job-' || v_suffix;
    v_snapshot_id := 'v027-verification-snapshot-' || v_suffix;
    v_source_version_id := 'v027-verification-source-' || v_suffix;
    v_target_version_id := 'v027-verification-target-' || v_suffix;
    v_blank_target_version_id := 'v027-verification-blank-target-' || v_suffix;
    v_revision_id := 'v027-verification-revision-' || v_suffix;

    -- 本次修改：建立最小版本链，验证人工修订可以引用 V026 的版本头、快照和作业。
    INSERT INTO meta.analysis_batch (batch_no, period, status, perspective, created_by)
    VALUES (v_batch_no, '2099-12', 'DRAFT', 'BASE', 'v027-verification')
    RETURNING id INTO v_batch_id;

    INSERT INTO flow.calculation_job (job_id, request_key, batch_no, status, attempt_no)
    VALUES (v_job_id, 'v027-verification-request-' || v_suffix, v_batch_no, 'SUCCEEDED', 1);

    INSERT INTO calc.calculation_snapshot (
        snapshot_id, snapshot_type, schema_version, content_json,
        source_version_set, content_hash, source_version_hash, rule_version, created_by
    ) VALUES (
        v_snapshot_id, 'INPUT', 'V027-1', '{"formula":"manual-revision"}'::jsonb,
        '["TASK-V027"]'::jsonb, 'hash-content-v027', 'hash-source-v027',
        'rule-v027', 'v027-verification'
    );

    INSERT INTO calc.calculation_version (
        calculation_version_id, batch_id, batch_no, perspective, version_no,
        job_id, snapshot_id, source_version_hash, config_hash, input_fingerprint,
        status, current_flag, calculated_by, calculated_at, trace_id
    ) VALUES (
        v_source_version_id, v_batch_id, v_batch_no, 'BASE', 'V027-SOURCE',
        v_job_id, v_snapshot_id, 'hash-source-v027', 'hash-config-v027',
        'hash-input-v027', 'PUBLISHED', TRUE, 'original-calculator',
        CURRENT_TIMESTAMP, 'trace-v027-source'
    );

    -- 本次修改：旧版本只转换为非当前/已被替代状态，版本主键和业务结果不被覆盖。
    UPDATE calc.calculation_version
       SET current_flag = FALSE, status = 'SUPERSEDED'
     WHERE calculation_version_id = v_source_version_id;

    INSERT INTO calc.calculation_version (
        calculation_version_id, batch_id, batch_no, perspective, version_no,
        job_id, snapshot_id, source_version_hash, config_hash, input_fingerprint,
        status, current_flag, supersedes_version_id, calculated_by, calculated_at, trace_id
    ) VALUES (
        v_target_version_id, v_batch_id, v_batch_no, 'BASE', 'V027-TARGET',
        v_job_id, v_snapshot_id, 'hash-source-v027', 'hash-config-v027',
        'hash-input-v027', 'CALCULATED', TRUE, v_source_version_id,
        'manual-reviser', CURRENT_TIMESTAMP, 'trace-v027-target'
    );

    -- 本次修改：准备一个非当前目标版本，单独验证修订原因非空约束而不混入外键/关系约束失败。
    INSERT INTO calc.calculation_version (
        calculation_version_id, batch_id, batch_no, perspective, version_no,
        job_id, snapshot_id, source_version_hash, config_hash, input_fingerprint,
        status, current_flag, supersedes_version_id, calculated_by, calculated_at, trace_id
    ) VALUES (
        v_blank_target_version_id, v_batch_id, v_batch_no, 'BASE', 'V027-BLANK-TARGET',
        v_job_id, v_snapshot_id, 'hash-source-v027', 'hash-config-v027',
        'hash-input-v027', 'SUPERSEDED', FALSE, v_source_version_id,
        'manual-reviser', CURRENT_TIMESTAMP, 'trace-v027-blank-target'
    );

    INSERT INTO calc.calculation_manual_revision (
        revision_id, source_calculation_version_id, target_calculation_version_id,
        revised_by, revision_reason, revised_at, trace_id
    ) VALUES (
        v_revision_id, v_source_version_id, v_target_version_id,
        'manual-reviser', '补录人工修订原因', CURRENT_TIMESTAMP, 'trace-v027-target'
    );

    -- 本次修改：独立记录生命周期事件，避免只依赖修订关系表推断事件发生。
    INSERT INTO flow.calculation_event_outbox (
        event_id, aggregate_type, aggregate_id, event_type,
        calculation_version_id, trace_id, payload_json
    ) VALUES (
        'v027-verification-event-' || v_suffix, 'CALCULATION_VERSION',
        v_target_version_id, 'MANUAL_REVISED', v_target_version_id,
        'trace-v027-target',
        jsonb_build_object(
            'revisionId', v_revision_id,
            'sourceVersionId', v_source_version_id,
            'targetVersionId', v_target_version_id,
            'revisedBy', 'manual-reviser',
            'revisionReason', '补录人工修订原因'
        )
    );

    SELECT current_flag INTO v_source_current
      FROM calc.calculation_version
     WHERE calculation_version_id = v_source_version_id;
    SELECT current_flag INTO v_target_current
      FROM calc.calculation_version
     WHERE calculation_version_id = v_target_version_id;
    IF v_source_current OR NOT v_target_current THEN
        RAISE EXCEPTION 'V027 current version pointer behavior failed';
    END IF;

    SELECT count(*) INTO v_event_count
      FROM flow.calculation_event_outbox
     WHERE calculation_version_id = v_target_version_id
       AND event_type = 'MANUAL_REVISED';
    IF v_event_count <> 1 THEN
        RAISE EXCEPTION 'Expected one MANUAL_REVISED lifecycle event';
    END IF;

    BEGIN
        INSERT INTO calc.calculation_manual_revision (
            revision_id, source_calculation_version_id, target_calculation_version_id,
            revised_by, revision_reason, revised_at
        ) VALUES (
            'v027-verification-duplicate-' || v_suffix, v_source_version_id,
            v_target_version_id, 'manual-reviser', '重复目标版本', CURRENT_TIMESTAMP
        );
    EXCEPTION
        WHEN unique_violation THEN
            v_duplicate_rejected := true;
    END;
    IF NOT v_duplicate_rejected THEN
        RAISE EXCEPTION 'Expected duplicate target version revision to be rejected';
    END IF;

    BEGIN
        INSERT INTO calc.calculation_manual_revision (
            revision_id, source_calculation_version_id, target_calculation_version_id,
            revised_by, revision_reason, revised_at
        ) VALUES (
            'v027-verification-blank-' || v_suffix, v_source_version_id,
            v_blank_target_version_id, 'manual-reviser',
            '   ', CURRENT_TIMESTAMP
        );
    EXCEPTION
        WHEN check_violation THEN
            v_blank_reason_rejected := true;
    END;
    IF NOT v_blank_reason_rejected THEN
        RAISE EXCEPTION 'Expected blank revision reason to be rejected';
    END IF;
END
$$;

ROLLBACK;

SELECT 'V027 manual calculation revision verification passed.' AS verification_result;
