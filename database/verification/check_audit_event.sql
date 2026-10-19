-- V020 audit.audit_event structure and insert contract verification.
-- Run after migrations V001-V020:
--   psql -v ON_ERROR_STOP=1 -f database/verification/check_audit_event.sql
-- The probe transaction is rolled back and leaves no audit rows behind.

DO $$
DECLARE
    v_table_oid oid;
    v_column_name text;
    v_required_columns text[] := ARRAY[
        'event_id', 'trace_id', 'operator', 'service', 'action', 'object_type',
        'object_id', 'from_state', 'to_state', 'result', 'reason', 'metadata',
        'occurred_at'
    ];
BEGIN
    SELECT c.oid
      INTO v_table_oid
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'audit'
       AND c.relname = 'audit_event'
       AND c.relkind IN ('r', 'p');

    IF v_table_oid IS NULL THEN
        RAISE EXCEPTION 'Missing table audit.audit_event';
    END IF;

    FOREACH v_column_name IN ARRAY v_required_columns LOOP
        IF NOT EXISTS (
            SELECT 1
              FROM pg_attribute
             WHERE attrelid = v_table_oid
               AND attname = v_column_name
               AND NOT attisdropped
               AND attnum > 0
        ) THEN
            RAISE EXCEPTION 'Missing column audit.audit_event.%', v_column_name;
        END IF;
    END LOOP;

    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'audit'
           AND table_name = 'audit_event'
           AND column_name IN ('event_id', 'operator', 'service', 'action', 'object_type',
                               'object_id', 'result', 'metadata', 'occurred_at')
           AND is_nullable <> 'NO'
    ) THEN
        RAISE EXCEPTION 'Required audit.audit_event column is nullable';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conrelid = v_table_oid
           AND conname = 'ck_audit_event_metadata_object'
           AND contype = 'c'
    ) THEN
        RAISE EXCEPTION 'Missing audit metadata object constraint';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_class c
         JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'audit' AND c.relname = 'idx_audit_event_occurred_at'
    ) THEN
        RAISE EXCEPTION 'Missing audit occurred_at index';
    END IF;
END
$$;

BEGIN;

INSERT INTO audit.audit_event (
    event_id, trace_id, operator, service, action, object_type, object_id,
    from_state, to_state, result, reason, metadata, occurred_at
) VALUES (
    'audit-event-verification', NULL, 'verification', 'verification', 'PROBE',
    'audit_event', 'audit-event-verification', NULL, NULL, 'SUCCESS', NULL,
    '{"probe":true}'::jsonb, CURRENT_TIMESTAMP
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM audit.audit_event
         WHERE event_id = 'audit-event-verification'
           AND trace_id IS NULL
           AND metadata = '{"probe": true}'::jsonb
    ) THEN
        RAISE EXCEPTION 'Audit event insert contract failed';
    END IF;
END
$$;

DO $$
DECLARE
    v_duplicate_rejected BOOLEAN := FALSE;
    v_metadata_rejected BOOLEAN := FALSE;
BEGIN
    BEGIN
        INSERT INTO audit.audit_event (
            event_id, trace_id, operator, service, action, object_type, object_id,
            from_state, to_state, result, reason, metadata, occurred_at
        ) VALUES (
            'audit-event-verification', NULL, 'verification', 'verification', 'DUPLICATE',
            'audit_event', 'audit-event-verification-duplicate', NULL, NULL, 'SUCCESS', NULL,
            '{"probe":true}'::jsonb, CURRENT_TIMESTAMP
        );
    EXCEPTION
        WHEN unique_violation THEN
            v_duplicate_rejected := TRUE;
    END;

    IF NOT v_duplicate_rejected THEN
        RAISE EXCEPTION 'Duplicate audit event_id was accepted';
    END IF;

    BEGIN
        INSERT INTO audit.audit_event (
            event_id, trace_id, operator, service, action, object_type, object_id,
            from_state, to_state, result, reason, metadata, occurred_at
        ) VALUES (
            'audit-event-verification-non-object', NULL, 'verification', 'verification', 'INVALID_METADATA',
            'audit_event', 'audit-event-verification-non-object', NULL, NULL, 'SUCCESS', NULL,
            '[]'::jsonb, CURRENT_TIMESTAMP
        );
    EXCEPTION
        WHEN check_violation THEN
            v_metadata_rejected := TRUE;
    END;

    IF NOT v_metadata_rejected THEN
        RAISE EXCEPTION 'Non-object audit metadata was accepted';
    END IF;
END
$$;

ROLLBACK;

SELECT 'V020 audit.audit_event structure and insert verification passed.'
    AS verification_result;
