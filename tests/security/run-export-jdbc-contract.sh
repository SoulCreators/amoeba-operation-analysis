#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"

if [[ -z "${AMOEBA_DB_URL:-}" ]]; then
  echo "AMOEBA_DB_URL is required for the PostgreSQL export repository contract" >&2
  exit 2
fi

cd "$repo_root"
exec mvn -pl backend/analysis-infrastructure -am \
  -Dtest=ExportTaskDbProfileIsolationContractTest,JdbcExportTaskRepositorySecurityContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
