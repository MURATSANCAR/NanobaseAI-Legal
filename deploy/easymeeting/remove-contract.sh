#!/usr/bin/env bash
# Backup and fully remove Contract Intelligence (old legal) from the portal host.
set -euo pipefail

BACKUP_ROOT="${BACKUP_ROOT:-/data/backups}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
BACKUP_DIR="${BACKUP_ROOT}/contract-${STAMP}"
COMPOSE_DIR="/data/nanobaseai/mobile-qa/contract-intelligence/deployment/docker-compose"
ENV_FILE="/etc/nanobaseai/contract.env"
DATA_DIR="/data/nanobaseai/contract"
NGINX_SITE="/etc/nginx/sites-enabled/portal.nanobase.ai"

need_cmd() { command -v "$1" >/dev/null 2>&1 || { echo "missing: $1" >&2; exit 1; }; }
need_cmd docker
need_cmd sudo

echo "==> Backup to ${BACKUP_DIR}"
sudo mkdir -p "${BACKUP_DIR}"
if [[ -f "${ENV_FILE}" ]]; then
  sudo cp -a "${ENV_FILE}" "${BACKUP_DIR}/contract.env"
fi
if [[ -d "${DATA_DIR}" ]]; then
  sudo tar -C /data/nanobaseai -czf "${BACKUP_DIR}/contract-data.tgz" contract || true
fi
if [[ -f "${COMPOSE_DIR}/docker-compose.yml" ]]; then
  sudo cp -a "${COMPOSE_DIR}/docker-compose.yml" "${BACKUP_DIR}/docker-compose.yml"
fi
if [[ -f "${NGINX_SITE}" ]]; then
  sudo cp -a "${NGINX_SITE}" "${BACKUP_DIR}/portal.nanobase.ai.bak"
fi

echo "==> Stopping nanobase-contract.service"
sudo systemctl stop nanobase-contract.service || true
sudo systemctl disable nanobase-contract.service || true

if [[ -f "${COMPOSE_DIR}/docker-compose.yml" ]]; then
  echo "==> docker compose down -v"
  cd "${COMPOSE_DIR}"
  if [[ -f "${ENV_FILE}" ]]; then
    sudo docker compose --env-file "${ENV_FILE}" down -v --remove-orphans || true
  else
    sudo docker compose down -v --remove-orphans || true
  fi
fi

echo "==> Removing leftover contract containers (best-effort)"
sudo docker ps -aq --filter "name=docker-compose-contract" | xargs -r sudo docker rm -f || true
sudo docker ps -aq --filter "name=docker-compose-" | while read -r id; do
  name="$(sudo docker inspect -f '{{.Name}}' "$id" 2>/dev/null || true)"
  case "$name" in
    /docker-compose-postgres-1|/docker-compose-redis-1|/docker-compose-minio-1|/docker-compose-qdrant-1|/docker-compose-document-intelligence-1|/docker-compose-embedding-service-1|/docker-compose-ai-gateway-1|/docker-compose-contract-api-1|/docker-compose-contract-worker-1)
      sudo docker rm -f "$id" || true
      ;;
  esac
done

echo "==> Removing contract data + env"
if [[ -d "${DATA_DIR}" ]]; then
  sudo rm -rf "${DATA_DIR}"
fi
if [[ -f "${ENV_FILE}" ]]; then
  sudo rm -f "${ENV_FILE}"
fi

echo "==> Patching nginx: remove /contracts-api"
if [[ -f "${NGINX_SITE}" ]]; then
  sudo python3 - <<'PY'
from pathlib import Path
path = Path("/etc/nginx/sites-enabled/portal.nanobase.ai")
text = path.read_text()
import re
text2 = re.sub(
    r"\nupstream contract_api \{.*?\n\}\n",
    "\n",
    text,
    count=1,
    flags=re.S,
)
text2 = re.sub(
    r"\n\s*# Contract Intelligence API.*?location /contracts-api/ \{.*?\n\s*\}\n",
    "\n",
    text2,
    count=1,
    flags=re.S,
)
if text2 != text:
    path.write_text(text2)
    print("nginx site updated")
else:
    print("nginx site unchanged (patterns not found)")
PY
fi

SNIPPET_AUTH="/etc/nginx/snippets/contracts-api-auth.conf"
if [[ -f "${SNIPPET_AUTH}" ]]; then
  sudo mv "${SNIPPET_AUTH}" "${BACKUP_DIR}/contracts-api-auth.conf"
fi

echo "==> nginx -t && reload"
sudo nginx -t
sudo systemctl reload nginx

echo "==> Contract Intelligence removed. Backup: ${BACKUP_DIR}"
