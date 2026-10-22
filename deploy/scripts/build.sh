#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${PROJECT_ROOT}"
node scripts/check-architecture.mjs
mvn -B -Dmaven.repo.local="${MAVEN_REPO_LOCAL:-${PROJECT_ROOT}/.toolchain/m2}" clean verify
cd frontend
npm ci
npm run test:unit
npm run build
