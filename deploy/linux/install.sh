#!/usr/bin/env bash
set -Eeuo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

usage() {
  echo "Usage: sudo $0 --env /secure/path/mes.env"
}

env_source=""
while (($#)); do
  case "$1" in
    --env) [[ $# -ge 2 ]] || die "--env requires a file"; env_source="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; die "unknown argument: $1" ;;
  esac
done

require_root
require_docker
require_command curl
[[ -n "${env_source}" ]] || { usage >&2; die "--env is required"; }
[[ ! -e "${CURRENT_LINK}" && ! -L "${CURRENT_LINK}" ]] \
  || die "already installed; use upgrade.sh"
validate_env "${env_source}"
verify_bundle
version="$(bundle_version)"

prepare_directories
install -m 0600 "${env_source}" "${ENV_FILE}"
target="$(install_release_files "${version}")"
load_images
require_release_images "${target}"

compose_release "${target}" config --quiet
activate_release "${target}"
cleanup_failed_install() {
  log "installation failed; stopping partially started stack"
  compose_release "${target}" down || true
  rm -f -- "${CURRENT_LINK}"
}
trap cleanup_failed_install ERR
compose_release "${target}" up --detach --pull never --no-build
wait_ready "${target}"
trap - ERR
log "installation complete: version ${version}"
