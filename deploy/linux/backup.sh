#!/usr/bin/env bash
set -Eeuo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

usage() {
  echo "Usage: sudo $0"
}

[[ $# -eq 0 ]] || { usage >&2; die "backup accepts no arguments"; }
require_root
require_docker
validate_env "${ENV_FILE}"
release="$(current_release)"

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
name="${stamp}-$(release_version "${release}")"
partial="${INSTALL_ROOT}/backups/.${name}.partial"
final="${INSTALL_ROOT}/backups/${name}"
[[ ! -e "${partial}" && ! -e "${final}" ]] || die "backup already exists: ${name}"
install -d -m 0700 -o 10001 -g 10001 "${partial}"
cleanup() { rm -rf -- "${partial}"; }
trap cleanup ERR INT TERM

log "backing up MySQL and SQLite as one set"
compose_release "${release}" exec -T mysql sh -eu -c '
  MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump \
    --host=127.0.0.1 --user="$MYSQL_USER" \
    --single-transaction --quick --triggers --no-tablespaces \
    --set-gtid-purged=OFF --default-character-set=utf8mb4 \
    "$MYSQL_DATABASE"
' > "${partial}/business.sql"
[[ -s "${partial}/business.sql" ]] || die "MySQL dump is empty"

compose_release "${release}" exec -T app \
  sqlite3 /app/data/mes.db ".timeout 10000" "PRAGMA wal_checkpoint(FULL);" \
  ".backup '/app/backups/.${name}.partial/telemetry.db'"
integrity="$(compose_release "${release}" exec -T app \
  sqlite3 "/app/backups/.${name}.partial/telemetry.db" "PRAGMA integrity_check;")"
[[ "${integrity//$'\r'/}" == "ok" ]] || die "SQLite backup integrity check failed"

(
  cd "${partial}"
  sha256sum business.sql telemetry.db > files.sha256
)
cat > "${partial}/manifest.txt" <<EOF
created_at=${stamp}
mes_version=$(release_version "${release}")
mysql_database=$(read_env_value "${ENV_FILE}" MYSQL_DATABASE)
mysql_file=business.sql
sqlite_file=telemetry.db
EOF
chmod 0600 "${partial}"/*
mv -- "${partial}" "${final}"
trap - ERR INT TERM
log "dual-database backup complete: ${final}"
