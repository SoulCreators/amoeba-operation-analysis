DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'flow' AND table_name = 'configuration_import_issue'
    ) THEN
        RAISE EXCEPTION 'Missing table flow.configuration_import_issue';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu
          ON kcu.constraint_name = tc.constraint_name
         AND kcu.table_schema = tc.table_schema
        WHERE tc.table_schema = 'flow'
          AND tc.table_name = 'configuration_import_issue'
          AND tc.constraint_type = 'FOREIGN KEY'
          AND kcu.column_name = 'configuration_task_id'
    ) THEN
        RAISE EXCEPTION 'Missing configuration import issue foreign key';
    END IF;
END
$$;

SELECT 'configuration import issue verification passed.' AS verification_result;
