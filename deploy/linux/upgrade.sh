#!/usr/bin/env bash
set -Eeuo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

usage() {
  echo "Usage: sudo $0 [--env /secure/path/mes.env] [--skip-backup]"
}

env_source=""
skip_backup=false
while (($#)); do
  case "$1" in
    --env) [[ $# -ge 2 ]] || die "--env requires a file"; env_source="$2"; shift 2 ;;
    --skip-backup) skip_backup=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; die "unknown argument: $1" ;;
  esac
done

require_root
require_docker
require_command curl
current="$(current_release)"
if [[ -n "${env_source}" ]]; then
  validate_env "${env_source}"
else
  validate_env "${ENV_FILE}"
fi
verify_bundle
version="$(bundle_version)"

if [[ "${skip_backup}" == false ]]; then
  "${current}/linux/backup.sh"
fi

prepare_directories
saved_env=""
if [[ -n "${env_source}" ]]; then
  saved_env="${INSTALL_ROOT}/config/.mes.env.upgrade.$$"
  install -m 0600 "${ENV_FILE}" "${saved_env}"
  install -m 0600 "${env_source}" "${ENV_FILE}"
fi
target="$(install_release_files "${version}")"
load_images
require_release_images "${target}"
compose_release "${target}" config --quiet

rollback_on_error() {
  log "upgrade failed; restoring previous containers"
  if [[ -n "${saved_env}" && -f "${saved_env}" ]]; then
    install -m 0600 "${saved_env}" "${ENV_FILE}"
  fi
  compose_release "${current}" up --detach --pull never --no-build || true
}
trap rollback_on_error ERR
compose_release "${target}" up --detach --pull never --no-build
wait_ready "${target}"
trap - ERR
[[ -z "${saved_env}" ]] || rm -f -- "${saved_env}"

activate_release "${target}"
log "upgrade complete: $(release_version "${current}") -> ${version}"
