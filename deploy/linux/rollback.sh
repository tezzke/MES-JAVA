#!/usr/bin/env bash
set -Eeuo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

usage() {
  echo "Usage: sudo $0 [--version VERSION] [--skip-backup]"
}

requested=""
skip_backup=false
while (($#)); do
  case "$1" in
    --version) [[ $# -ge 2 ]] || die "--version requires a value"; requested="$2"; shift 2 ;;
    --skip-backup) skip_backup=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; die "unknown argument: $1" ;;
  esac
done

require_root
require_docker
require_command curl
validate_env "${ENV_FILE}"
current="$(current_release)"
if [[ -n "${requested}" ]]; then
  [[ "${requested}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || die "invalid version"
  target="${RELEASES_DIR}/${requested}"
else
  [[ -L "${PREVIOUS_LINK}" ]] || die "no previous release recorded; pass --version"
  target="$(readlink -f "${PREVIOUS_LINK}")"
fi
[[ -d "${target}" ]] || die "release not installed: ${target}"
[[ "$(readlink -f "${target}")" != "${current}" ]] || die "target is already current"
require_release_images "${target}"

if [[ "${skip_backup}" == false ]]; then
  "${current}/linux/backup.sh"
fi
compose_release "${target}" config --quiet

restore_current() {
  log "rollback failed; restoring current release"
  compose_release "${current}" up --detach --pull never --no-build || true
}
trap restore_current ERR
compose_release "${target}" up --detach --pull never --no-build
wait_ready "${target}"
trap - ERR

activate_release "${target}"
log "rollback complete: $(release_version "${current}") -> $(release_version "${target}")"
