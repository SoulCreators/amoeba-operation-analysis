#!/usr/bin/env bash
set -euo pipefail

mvn -B -DskipTests clean package
cd frontend
# 当前骨架尚未提交锁定文件，先使用 npm install；稳定依赖版本后再提交 package-lock.json 并切回 npm ci。
npm install
npm run build
