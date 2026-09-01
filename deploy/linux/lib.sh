#!/usr/bin/env bash
set -Eeuo pipefail

readonly INSTALL_ROOT="/opt/mes"
readonly RELEASES_DIR="${INSTALL_ROOT}/releases"
readonly CURRENT_LINK="${INSTALL_ROOT}/current"
readonly PREVIOUS_LINK="${INSTALL_ROOT}/previous"
readonly ENV_FILE="${INSTALL_ROOT}/config/mes.env"
readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly BUNDLE_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

log() { printf '[MES] %s\n' "$*"; }
die() { printf '[MES] ERROR: %s\n' "$*" >&2; exit 1; }

require_root() {
  [[ "${EUID}" -eq 0 ]] || die "must run as root"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

require_docker() {
  require_command docker
  docker compose version >/dev/null 2>&1 || die "Docker Compose v2 is required"
}

read_env_value() {
  local file="$1" key="$2"
  awk -v key="${key}" '
    index($0, key "=") == 1 {
      sub("^[^=]*=", "")
      sub("\r$", "")
      print
      exit
    }
  ' "${file}"
}

validate_env() {
  local file="$1" key value origin authority database port
  [[ -f "${file}" ]] || die "environment file not found: ${file}"
  for key in MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD MYSQL_ROOT_PASSWORD MES_ALLOWED_ORIGIN; do
    value="$(read_env_value "${file}" "${key}")"
    [[ -n "${value}" ]] || die "${key} must not be empty"
    [[ "${value}" != *CHANGE_ME* ]] || die "${key} still contains CHANGE_ME"
  done
  database="$(read_env_value "${file}" MYSQL_DATABASE)"
  [[ "${database}" =~ ^[A-Za-z0-9_]+$ ]] || die "MYSQL_DATABASE must contain only letters, digits, underscore"
  origin="$(read_env_value "${file}" MES_ALLOWED_ORIGIN)"
  [[ "${origin}" =~ ^http://[A-Za-z0-9._-]+(:[0-9]{1,5})?$ ]] \
    || die "MES_ALLOWED_ORIGIN must be one exact HTTP origin without path or trailing slash"
  authority="${origin#http://}"
  if [[ "${authority}" == *:* ]]; then
    port="${authority##*:}"
    ((port >= 1 && port <= 65535)) || die "MES_ALLOWED_ORIGIN port is out of range"
  fi
  for key in HTTP_PORT SCANNER_PORT; do
    value="$(read_env_value "${file}" "${key}")"
    [[ -z "${value}" ]] && continue
    [[ "${value}" =~ ^[0-9]+$ ]] || die "${key} must be numeric"
    ((10#${value} >= 1 && 10#${value} <= 65535)) || die "${key} is out of range"
  done
}

bundle_version() {
  local version
  [[ -f "${BUNDLE_ROOT}/VERSION" ]] || die "VERSION is missing"
  version="$(tr -d '\r\n' < "${BUNDLE_ROOT}/VERSION")"
  [[ "${version}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || die "invalid VERSION: ${version}"
  printf '%s' "${version}"
}

release_version() {
  local release="$1" version
  [[ -f "${release}/VERSION" ]] || die "release VERSION is missing: ${release}"
  version="$(tr -d '\r\n' < "${release}/VERSION")"
  [[ "${version}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || die "invalid release VERSION"
  printf '%s' "${version}"
}

verify_bundle() {
  require_command sha256sum
  [[ -f "${BUNDLE_ROOT}/manifest.sha256" ]] || die "manifest.sha256 is missing"
  (cd "${BUNDLE_ROOT}" && sha256sum --check --strict manifest.sha256)
}

load_images() {
  [[ -f "${BUNDLE_ROOT}/images-amd64.tar" ]] || die "images-amd64.tar is missing"
  log "loading offline images"
  docker load --input "${BUNDLE_ROOT}/images-amd64.tar" >/dev/null
}

require_release_images() {
  local release="$1" version image platform
  version="$(release_version "${release}")"
  for image in "mes-app:${version}" "mes-nginx:${version}" "mysql:8.4.6"; do
    docker image inspect "${image}" >/dev/null 2>&1 || die "offline image is missing: ${image}"
    platform="$(docker image inspect --format '{{.Os}}/{{.Architecture}}' "${image}")"
    [[ "${platform}" == "linux/amd64" ]] || die "image is not linux/amd64: ${image} (${platform})"
  done
}

prepare_directories() {
  install -d -m 0750 "${INSTALL_ROOT}" "${RELEASES_DIR}"
  install -d -m 0700 "${INSTALL_ROOT}/mysql" "${INSTALL_ROOT}/telemetry" \
    "${INSTALL_ROOT}/backups"
  install -d -m 0750 -o root -g 10001 "${INSTALL_ROOT}/config"
  chown 10001:10001 "${INSTALL_ROOT}/telemetry" "${INSTALL_ROOT}/backups"
  # 首装才铺默认配置:升级时保留现场已调好的点表与厂房模型
  local name
  for name in devices.json plant.json; do
    if [[ ! -f "${INSTALL_ROOT}/config/${name}" ]]; then
      install -m 0640 -o root -g 10001 \
        "${BUNDLE_ROOT}/${name}" "${INSTALL_ROOT}/config/${name}"
    fi
  done
}

install_release_files() {
  local version="$1" target="${RELEASES_DIR}/$1"
  install -d -m 0750 "${target}/linux"
  install -m 0644 "${BUNDLE_ROOT}/docker-compose.offline.yml" "${target}/docker-compose.offline.yml"
  install -m 0644 "${BUNDLE_ROOT}/VERSION" "${target}/VERSION"
  install -m 0755 "${BUNDLE_ROOT}"/linux/*.sh "${target}/linux/"
  printf '%s' "${target}"
}

compose_release() {
  local release="$1"; shift
  local version
  version="$(release_version "${release}")"
  MES_VERSION="${version}" docker compose \
    --project-name mes \
    --env-file "${ENV_FILE}" \
    --file "${release}/docker-compose.offline.yml" \
    "$@"
}

activate_release() {
  local target="$1"
  if [[ -L "${CURRENT_LINK}" \
        && "$(readlink -f "${CURRENT_LINK}")" != "$(readlink -f "${target}")" ]]; then
    ln -sfn "$(readlink -f "${CURRENT_LINK}")" "${PREVIOUS_LINK}"
  fi
  ln -sfn "${target}" "${CURRENT_LINK}"
}

wait_ready() {
  local release="$1" port attempt
  port="$(read_env_value "${ENV_FILE}" HTTP_PORT)"
  port="${port:-80}"
  for attempt in {1..40}; do
    if curl --fail --silent --show-error \
      "http://127.0.0.1:${port}/api/system/readiness" >/dev/null 2>&1; then
      log "release $(release_version "${release}") is ready"
      return 0
    fi
    sleep 3
  done
  compose_release "${release}" ps >&2 || true
  die "readiness timed out"
}

current_release() {
  [[ -L "${CURRENT_LINK}" ]] || die "MES is not installed"
  readlink -f "${CURRENT_LINK}"
}
