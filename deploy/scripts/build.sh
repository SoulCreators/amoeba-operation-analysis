#!/usr/bin/env bash
set -euo pipefail

mvn -B -s "$(cd "$(dirname "${BASH_SOURCE[0]}")/../config" && pwd)/maven-settings.xml" \
  -Dmaven.repo.local="${MAVEN_REPO_LOCAL:-.toolchain/m2}" -DskipTests clean package
cd frontend
npm ci
npm run build
