#!/usr/bin/env bash
set -euo pipefail

: "${BACKUP_OUTPUT:?BACKUP_OUTPUT is required}"
: "${NEW_BACKEND_IMAGE:?NEW_BACKEND_IMAGE is required}"
: "${NEW_FRONTEND_IMAGE:?NEW_FRONTEND_IMAGE is required}"
: "${NEW_DOCUMENT_INTELLIGENCE_IMAGE:?NEW_DOCUMENT_INTELLIGENCE_IMAGE is required}"
: "${NEW_AI_ORCHESTRATOR_IMAGE:?NEW_AI_ORCHESTRATOR_IMAGE is required}"

scripts/validate-installation.sh
scripts/backup.sh "$BACKUP_OUTPUT"

BACKEND_IMAGE="$NEW_BACKEND_IMAGE" \
FRONTEND_IMAGE="$NEW_FRONTEND_IMAGE" \
DOCUMENT_INTELLIGENCE_IMAGE="$NEW_DOCUMENT_INTELLIGENCE_IMAGE" \
AI_ORCHESTRATOR_IMAGE="$NEW_AI_ORCHESTRATOR_IMAGE" \
docker compose --file compose.yaml --file compose.production.yaml \
  up --detach --no-deps backend
scripts/health-check.sh

BACKEND_IMAGE="$NEW_BACKEND_IMAGE" \
FRONTEND_IMAGE="$NEW_FRONTEND_IMAGE" \
DOCUMENT_INTELLIGENCE_IMAGE="$NEW_DOCUMENT_INTELLIGENCE_IMAGE" \
AI_ORCHESTRATOR_IMAGE="$NEW_AI_ORCHESTRATOR_IMAGE" \
docker compose --file compose.yaml --file compose.production.yaml \
  up --detach --no-deps document-intelligence ai-orchestrator frontend
scripts/health-check.sh
printf 'Upgrade health checks passed. Complete smoke and UAT gates before sign-off.\n'
