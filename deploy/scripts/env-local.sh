#!/usr/bin/env bash
# Source this helper from Bash or Zsh. Prefer caller-supplied or installed tools.
if [ -n "${ZSH_VERSION-}" ]; then
  AMOEBA_ENV_SCRIPT="${(%):-%N}"
else
  AMOEBA_ENV_SCRIPT="${BASH_SOURCE[0]}"
fi
AMOEBA_PROJECT_ROOT="$(cd "$(dirname "${AMOEBA_ENV_SCRIPT}")/../.." && pwd)"
if [ -z "${JAVA_HOME:-}" ]; then
  if [ -x "${AMOEBA_PROJECT_ROOT}/.toolchain/jdk8/Contents/Home/bin/java" ]; then
    export JAVA_HOME="${AMOEBA_PROJECT_ROOT}/.toolchain/jdk8/Contents/Home"
  elif [ -x /usr/libexec/java_home ]; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 1.8)"
  fi
fi
if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi
for AMOEBA_TOOL_DIR in maven node; do
  if [ -d "${AMOEBA_PROJECT_ROOT}/.toolchain/${AMOEBA_TOOL_DIR}/bin" ]; then
    export PATH="${AMOEBA_PROJECT_ROOT}/.toolchain/${AMOEBA_TOOL_DIR}/bin:${PATH}"
  fi
done
export MAVEN_REPO_LOCAL="${MAVEN_REPO_LOCAL:-${AMOEBA_PROJECT_ROOT}/.toolchain/m2}"
java -version && mvn -version && node --version && npm --version
