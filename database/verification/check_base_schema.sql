SELECT schema_name
FROM information_schema.schemata
WHERE schema_name IN ('iam', 'meta', 'raw', 'cfg', 'calc', 'mart', 'flow', 'audit')
ORDER BY schema_name;
