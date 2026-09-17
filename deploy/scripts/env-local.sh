#!/usr/bin/env bash
set -eo pipefail

# 用户级工具链环境：source deploy/scripts/env-local.sh
if [ -n "${ZSH_VERSION-}" ]; then
  SCRIPT_FILE="${(%):-%N}"
else
  SCRIPT_FILE="${BASH_SOURCE[0]}"
fi
PROJECT_ROOT="$(cd "$(dirname "${SCRIPT_FILE}")/../.." && pwd)"
export JAVA_HOME="${PROJECT_ROOT}/.toolchain/jdk8/Contents/Home"
export MAVEN_HOME="${PROJECT_ROOT}/.toolchain/maven"
export MAVEN_REPO_LOCAL="${PROJECT_ROOT}/.toolchain/m2"
export NODE_HOME="${PROJECT_ROOT}/.toolchain/node"
export PATH="${MAVEN_HOME}/bin:${NODE_HOME}/bin:${JAVA_HOME}/bin:${PATH}"

echo "JAVA_HOME=${JAVA_HOME}"
echo "MAVEN_HOME=${MAVEN_HOME}"
echo "MAVEN_REPO_LOCAL=${MAVEN_REPO_LOCAL}"
echo "NODE_HOME=${NODE_HOME}"
java -version
mvn -version
node --version
npm --version
