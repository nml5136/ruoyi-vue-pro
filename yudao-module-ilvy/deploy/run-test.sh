#!/usr/bin/env bash
set -euo pipefail
ILVY_DEPLOY_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ILVY_REPO_DIR="$(cd -- "$ILVY_DEPLOY_DIR/../.." && pwd)"
export ILVY_DEPLOY_DIR
: "${ILVY_DB_PASSWORD:?Set the approved isolated test database secret outside Git}"
: "${ILVY_TEST_DATA_DIR:?Set an absolute private persistent test directory ending in ilvy-test-data}"
if [[ "$ILVY_DB_PASSWORD" == SET_LOCALLY || "$ILVY_TEST_DATA_DIR" != /* || "${ILVY_TEST_DATA_DIR##*/}" != ilvy-test-data ]]; then
  echo 'Refusing placeholder secret or unsuitable test data directory' >&2; exit 2
fi
ILVY_JAR="$ILVY_REPO_DIR/yudao-server/target/yudao-server.jar"
[[ -f "$ILVY_JAR" ]] || { echo 'Build the ilvy-dev-host server artifact first' >&2; exit 2; }
if [[ "${1:-}" == --check ]]; then
  echo 'Configuration inputs present; no service or database was started or modified'; exit 0
fi
[[ $# == 0 ]] || { echo 'Usage: run-test.sh [--check]' >&2; exit 2; }
umask 077
mkdir -p -- "$ILVY_TEST_DATA_DIR/ilvy-evidence"
exec java -jar "$ILVY_JAR" "--spring.config.location=file:$ILVY_DEPLOY_DIR/application-test.properties"
