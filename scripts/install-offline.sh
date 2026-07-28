#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  printf 'Usage: %s /explicit/extracted-offline-bundle\n' "$0" >&2
  exit 64
fi
bundle="$1"
test -f "$bundle/SHA256SUMS"
(
  cd "$bundle"
  sha256sum --check SHA256SUMS
)
"$bundle/scripts/validate-installation.sh"
docker image load --input "$bundle/images/specai-images.tar"
docker compose \
  --file "$bundle/config/compose.yaml" \
  --file "$bundle/config/compose.production.yaml" \
  up --detach --pull never
printf 'Offline installation started. Run health-check.sh before acceptance.\n'
