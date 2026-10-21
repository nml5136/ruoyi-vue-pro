#!/usr/bin/env bash
set -euo pipefail
[[ "${ILVY_ALLOW_EMPTY_TEST_INIT:-}" == YES ]] || { echo 'Explicit empty isolated test initialization approval is required' >&2; exit 2; }
: "${ILVY_COMPOSE_ENV:?Set the path of the private compose environment file}"
ILVY_DEPLOY_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ILVY_REPO_DIR="$(cd -- "$ILVY_DEPLOY_DIR/../.." && pwd)"
compose() { docker compose --env-file "$ILVY_COMPOSE_ENV" -p ilvy-test -f "$ILVY_DEPLOY_DIR/compose.yaml" "$@"; }
mysql_test() { compose exec -T mysql sh -ec 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --default-character-set=utf8mb4 "$MYSQL_DATABASE"'; }
ILVY_TABLE_COUNT="$(printf '%s\n' 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE();' | mysql_test | tail -n 1)"
[[ "$ILVY_TABLE_COUNT" == 0 ]] || { echo 'Database is not empty; refusing initialization or automatic retry' >&2; exit 2; }
mysql_test < "$ILVY_REPO_DIR/sql/mysql/ruoyi-vue-pro.sql"
# Disable upstream seed identities and outbound channels BEFORE the application starts.
printf '%s\n' 'UPDATE system_users SET status=1;' 'UPDATE system_sms_channel SET status=1;' 'UPDATE system_social_client SET status=1;' | mysql_test
mysql_test < "$ILVY_DEPLOY_DIR/../dev/host-member-fixture.sql"
printf '%s\n' 'CREATE TABLE ilvy_deploy_migration(name VARCHAR(128) PRIMARY KEY,sha256 CHAR(64) NOT NULL,applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);' | mysql_test
for migration in V1__ilvy.sql V2__ilvy_local_examples.sql V3__ilvy_owner_scope.sql V4__ilvy_private_evidence.sql V5__ilvy_catalog_index.sql V6__ilvy_evidence_lifecycle.sql; do
  ILVY_SQL="$ILVY_DEPLOY_DIR/../src/main/resources/db/migration/$migration"
  mysql_test < "$ILVY_SQL"
  ILVY_CHECKSUM="$(sha256sum "$ILVY_SQL" | cut -d ' ' -f 1)"
  printf "INSERT INTO ilvy_deploy_migration(name,sha256) VALUES ('%s','%s');\n" "$migration" "$ILVY_CHECKSUM" | mysql_test
done
echo 'Initialized isolated test schema. No member/test login account or destination content was created.'
echo 'If any step fails, keep the database and review the partial migration; do not rerun on a nonempty database.'
