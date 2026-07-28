#!/usr/bin/env bash
set -euo pipefail
umask 077

if [[ $# -ne 2 ]]; then
  printf 'Usage: %s image-list.txt /explicit/output/specai-offline.tar\n' "$0" >&2
  exit 64
fi
image_list="$1"
output="$2"
case "$output" in
  /|"$HOME"|".") printf 'Refusing broad package target.\n' >&2; exit 64 ;;
esac
if grep -E '(^|:)latest$' "$image_list"; then
  printf 'Offline image list contains a forbidden latest tag.\n' >&2
  exit 1
fi

stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT
mkdir -p "$stage/images" "$stage/config" "$stage/scripts" "$stage/docs"
docker image save --output "$stage/images/specai-images.tar" \
  $(sed '/^[[:space:]]*#/d;/^[[:space:]]*$/d' "$image_list")
cp compose.yaml compose.production.yaml "$stage/config/"
cp -R src/main/resources/db "$stage/config/"
cp scripts/validate-installation.sh scripts/backup.sh scripts/restore-test.sh "$stage/scripts/"
cp docs/OFFLINE-INSTALLATION.md docs/LICENSE-COMPLIANCE.md "$stage/docs/"
find "$stage" -type f -print0 | sort -z | xargs -0 sha256sum > "$stage/SHA256SUMS"
tar -C "$stage" -cf "$output" .
printf 'Offline bundle created without network access: %s\n' "$output"
