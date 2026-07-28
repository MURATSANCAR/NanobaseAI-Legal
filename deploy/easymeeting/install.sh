#!/usr/bin/env bash
# Install / refresh SpecAI Legal on the EasyMeeting (portal) host.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DEST="${LEGAL_DEST:-/data/nanobaseai/legal}"
ENV_FILE="${LEGAL_ENV_FILE:-/etc/nanobaseai/legal.env}"
ACTENORA_ENV="${ACTENORA_ENV_FILE:-/etc/nanobaseai/actenora.env}"

need_cmd() { command -v "$1" >/dev/null 2>&1 || { echo "missing: $1" >&2; exit 1; }; }
need_cmd docker
need_cmd openssl
need_cmd sudo

echo "==> Syncing repo to ${DEST}"
sudo mkdir -p "${DEST}"
sudo rsync -a --delete \
  --exclude '.git' \
  --exclude 'frontend/node_modules' \
  --exclude 'frontend/.pnpm-store' \
  --exclude 'target' \
  --exclude '.pytest_cache' \
  "${ROOT}/" "${DEST}/"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "==> Creating ${ENV_FILE}"
  # shellcheck disable=SC1090
  source <(sudo grep -E '^(POSTGRES_PASSWORD|RABBITMQ_PASSWORD|OBJECT_STORAGE_ACCESS_KEY|OBJECT_STORAGE_SECRET_KEY|REDIS_PASSWORD)=' "${ACTENORA_ENV}" | sed 's/^/export /')
  JWT_SECRET="$(openssl rand -base64 48 | tr -d '\n')"
  ADMIN_PASSWORD="$(openssl rand -base64 18 | tr -d '\n=/+' | cut -c1-20)"
  DB_PASSWORD="$(openssl rand -base64 24 | tr -d '\n=/+' | cut -c1-28)"
  sudo tee "${ENV_FILE}" >/dev/null <<EOF
SPECAI_AUTH_MODE=local
SPRING_PROFILES_ACTIVE=development
SPECAI_ENVIRONMENT=development
SPECAI_RELEASE_VERSION=development
DATABASE_USER=specai
DATABASE_PASSWORD=${DB_PASSWORD}
MINIO_ACCESS_KEY=${OBJECT_STORAGE_ACCESS_KEY}
MINIO_SECRET_KEY=${OBJECT_STORAGE_SECRET_KEY}
RABBITMQ_USER=actenora
RABBITMQ_PASSWORD=${RABBITMQ_PASSWORD}
REDIS_PASSWORD=${REDIS_PASSWORD:-}
SPECAI_JWT_SECRET=${JWT_SECRET}
SPECAI_LOCAL_ADMIN_EMAIL=admin@nanobase.local
SPECAI_LOCAL_ADMIN_PASSWORD=${ADMIN_PASSWORD}
SPECAI_BOOTSTRAP_TENANT_ID=11111111-1111-1111-1111-111111111111
SPECAI_BOOTSTRAP_TENANT_NAME=Nano Teknoloji A.Ş.
PUBLIC_API_BASE_URL=https://portal.nanobase.ai/legal-api
PUBLIC_BASE_PATH=/legal
ALLOWED_ORIGINS=https://portal.nanobase.ai
AI_MODEL_DEPLOYMENTS_JSON=[{"profile":"BALANCED","baseUrl":"http://host.docker.internal:8010","runtimeModel":"nanobase-qwen36-35b-a3b-mtp","timeoutSeconds":180}]
AI_ORCHESTRATOR_LOG_LEVEL=INFO
CLAMAV_ENABLED=true
FILE_SECURITY_FAIL_CLOSED=true
EOF
  sudo chmod 600 "${ENV_FILE}"
  echo "Admin password written to ${ENV_FILE} (SPECAI_LOCAL_ADMIN_PASSWORD)"
else
  echo "==> Reusing existing ${ENV_FILE}"
fi

# shellcheck disable=SC1090
source <(sudo grep -E '^(DATABASE_USER|DATABASE_PASSWORD|MINIO_ACCESS_KEY|MINIO_SECRET_KEY)=' "${ENV_FILE}" | sed 's/^/export /')
# shellcheck disable=SC1090
source <(sudo grep -E '^POSTGRES_PASSWORD=' "${ACTENORA_ENV}" | sed 's/^/export /')

echo "==> Ensuring Postgres role/database specai"
docker exec -i actenora-prodlike-postgres \
  psql -U actenora -d postgres <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${DATABASE_USER}') THEN
    CREATE ROLE ${DATABASE_USER} LOGIN PASSWORD '${DATABASE_PASSWORD}';
  ELSE
    ALTER ROLE ${DATABASE_USER} WITH LOGIN PASSWORD '${DATABASE_PASSWORD}';
  END IF;
END
\$\$;
SELECT 'CREATE DATABASE specai OWNER ${DATABASE_USER}'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'specai')\gexec
GRANT ALL PRIVILEGES ON DATABASE specai TO ${DATABASE_USER};
SQL
docker exec -i actenora-prodlike-postgres \
  psql -U actenora -d postgres -c "ALTER ROLE ${DATABASE_USER} BYPASSRLS;"

echo "==> Ensuring MinIO buckets"
docker run --rm --entrypoint=/bin/sh --network actenora-prodlike-data-network \
  -e MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY}" \
  -e MINIO_SECRET_KEY="${MINIO_SECRET_KEY}" \
  minio/mc:RELEASE.2025-04-16T18-13-26Z \
  -ec '
    mc alias set local http://actenora-prodlike-minio:9000 "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY";
    for bucket in specai-original specai-processed specai-thumbnails specai-reports specai-temp; do
      mc mb --ignore-existing "local/${bucket}";
      mc anonymous set none "local/${bucket}";
    done
  ' || echo "WARNING: MinIO bucket bootstrap failed (may already exist)"


echo "==> Installing systemd unit"
sudo cp "${DEST}/deploy/easymeeting/nanobase-legal.service" /etc/systemd/system/nanobase-legal.service
sudo systemctl daemon-reload
sudo systemctl enable nanobase-legal.service

echo "==> Building and starting SpecAI Legal stack"
cd "${DEST}"
sudo docker compose -f compose.yaml -f compose.easymeeting.yaml --env-file "${ENV_FILE}" up -d --build --remove-orphans

echo "==> Done. Smoke:"
echo "  curl -sS http://127.0.0.1:8098/actuator/health/readiness"
echo "  curl -sS -o /dev/null -w '%{http_code}\\n' http://127.0.0.1:3020/legal/"
