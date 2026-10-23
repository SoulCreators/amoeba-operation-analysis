SELECT column_name, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'meta'
  AND table_name = 'analysis_batch'
  AND column_name = 'scope_code';
