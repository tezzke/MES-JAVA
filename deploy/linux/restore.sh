#!/usr/bin/env bash
set -Eeuo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

usage() {
  echo "Usage: sudo $0 --backup BACKUP_DIRECTORY_NAME --confirm RESTORE"
}

backup_name=""
confirmation=""
while (($#)); do
  case "$1" in
    --backup) [[ $# -ge 2 ]] || die "--backup requires a name"; backup_name="$2"; shift 2 ;;
    --confirm) [[ $# -ge 2 ]] || die "--confirm requires RESTORE"; confirmation="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; die "unknown argument: $1" ;;
  esac
done

require_root
require_docker
[[ "${confirmation}" == "RESTORE" ]] || die "pass --confirm RESTORE to overwrite both databases"
[[ "${backup_name}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || die "invalid backup directory name"
validate_env "${ENV_FILE}"
release="$(current_release)"
backup="${INSTALL_ROOT}/backups/${backup_name}"
[[ -d "${backup}" ]] || die "backup not found: ${backup}"
for file in business.sql telemetry.db files.sha256 manifest.txt; do
  [[ -f "${backup}/${file}" ]] || die "backup file missing: ${file}"
done
(cd "${backup}" && sha256sum --check --strict files.sha256)
[[ -s "${backup}/business.sql" ]] || die "business.sql is empty"

integrity="$(compose_release "${release}" exec -T app \
  sqlite3 "/app/backups/${backup_name}/telemetry.db" "PRAGMA integrity_check;")"
[[ "${integrity//$'\r'/}" == "ok" ]] || die "SQLite source integrity check failed"

log "creating mandatory pre-restore safety backup"
"${release}/linux/backup.sh"
log "stopping traffic and application"
compose_release "${release}" stop nginx app

compose_release "${release}" exec -T mysql sh -eu -c '
  MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --host=127.0.0.1 --user=root \
    --execute="DROP DATABASE IF EXISTS \`$MYSQL_DATABASE\`; CREATE DATABASE \`$MYSQL_DATABASE\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
'
compose_release "${release}" exec -T mysql sh -eu -c '
  MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --host=127.0.0.1 --user="$MYSQL_USER" \
    --database="$MYSQL_DATABASE" --default-character-set=utf8mb4 --binary-mode=1
' < "${backup}/business.sql"

temporary="${INSTALL_ROOT}/telemetry/.mes.db.restore.$$"
install -m 0600 -o 10001 -g 10001 "${backup}/telemetry.db" "${temporary}"
rm -f -- "${INSTALL_ROOT}/telemetry/mes.db-wal" "${INSTALL_ROOT}/telemetry/mes.db-shm"
mv -f -- "${temporary}" "${INSTALL_ROOT}/telemetry/mes.db"
chown 10001:10001 "${INSTALL_ROOT}/telemetry/mes.db"

compose_release "${release}" up --detach --pull never --no-build
wait_ready "${release}"
log "dual-database restore complete from ${backup_name}"
