#!/usr/bin/env bash
set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly DEPLOY_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
readonly REPO_ROOT="$(cd -- "${DEPLOY_DIR}/.." && pwd)"
readonly MYSQL_IMAGE="mysql:8.4.6"

die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
require_command() { command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"; }
usage() { echo "Usage: $0 VERSION [OUTPUT_DIRECTORY]"; }

[[ $# -ge 1 && $# -le 2 ]] || { usage >&2; exit 2; }
version="$1"
output="${2:-${DEPLOY_DIR}/offline-dist}"
[[ "${version}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || die "invalid VERSION"

for command in docker tar sha256sum; do require_command "${command}"; done
docker buildx version >/dev/null 2>&1 || die "docker buildx is required"
docker info >/dev/null 2>&1 || die "Docker daemon is unavailable"

app_image="mes-app:${version}"
nginx_image="mes-nginx:${version}"
package_name="mes-${version}-linux-amd64"
stage="${output}/.${package_name}.staging"
archive="${output}/${package_name}.tar.gz"

mkdir -p "${output}"
rm -rf -- "${stage}"
mkdir -p "${stage}/linux"
trap 'rm -rf -- "${stage}"' EXIT

printf '[1/6] Building amd64 application image\n'
docker buildx build --platform linux/amd64 --load \
  --target app-runtime --tag "${app_image}" \
  --file "${DEPLOY_DIR}/Dockerfile" "${REPO_ROOT}"

printf '[2/6] Building amd64 Nginx/static image\n'
docker buildx build --platform linux/amd64 --load \
  --target nginx-runtime --tag "${nginx_image}" \
  --file "${DEPLOY_DIR}/Dockerfile" "${REPO_ROOT}"

printf '[3/6] Pulling pinned amd64 MySQL image\n'
docker pull --platform linux/amd64 "${MYSQL_IMAGE}"

for image in "${app_image}" "${nginx_image}" "${MYSQL_IMAGE}"; do
  [[ "$(docker image inspect --format '{{.Os}}/{{.Architecture}}' "${image}")" == "linux/amd64" ]] \
    || die "image is not linux/amd64: ${image}"
done

printf '[4/6] Saving images and delivery files\n'
docker save --output "${stage}/images-amd64.tar" \
  "${app_image}" "${nginx_image}" "${MYSQL_IMAGE}"
{
  printf '# image tag | local content id | repository digest (when available)\n'
  for image in "${app_image}" "${nginx_image}" "${MYSQL_IMAGE}"; do
    image_id="$(docker image inspect --format '{{.Id}}' "${image}")"
    repo_digests="$(docker image inspect --format '{{join .RepoDigests ","}}' "${image}")"
    printf '%s | %s | %s\n' "${image}" "${image_id}" "${repo_digests:--}"
  done
} > "${stage}/IMAGE-MANIFEST.txt"
printf '%s\n' "${version}" > "${stage}/VERSION"
cp "${DEPLOY_DIR}/docker-compose.offline.yml" "${stage}/"
cp "${DEPLOY_DIR}/.env.offline.example" "${stage}/.env.example"
cp "${DEPLOY_DIR}/OFFLINE-LINUX.md" "${stage}/README.md"
cp "${REPO_ROOT}/backend/mes-api/src/main/resources/devices.json" "${stage}/devices.json"
cp "${REPO_ROOT}/backend/mes-api/src/main/resources/plant.json" "${stage}/plant.json"
cp "${SCRIPT_DIR}"/*.sh "${stage}/linux/"
chmod 0755 "${stage}/linux/"*.sh

printf '[5/6] Writing SHA256 manifest\n'
(
  cd "${stage}"
  sha256sum VERSION IMAGE-MANIFEST.txt images-amd64.tar docker-compose.offline.yml \
    .env.example README.md devices.json plant.json linux/*.sh > manifest.sha256
)

printf '[6/6] Creating delivery archive\n'
rm -f -- "${archive}"
temporary="${output}/${package_name}"
rm -rf -- "${temporary}"
mv "${stage}" "${temporary}"
tar -C "${output}" -czf "${archive}" "${package_name}"
rm -rf -- "${temporary}"
trap - EXIT
(cd "${output}" && sha256sum "${package_name}.tar.gz" > "${package_name}.tar.gz.sha256")
printf 'Created %s\n' "${archive}"
