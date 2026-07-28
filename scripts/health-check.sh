#!/usr/bin/env bash
set -euo pipefail

: "${HEALTH_BASE_URL:?HEALTH_BASE_URL is required}"
curl --fail --silent --show-error "${HEALTH_BASE_URL}/actuator/health/liveness"
curl --fail --silent --show-error "${HEALTH_BASE_URL}/actuator/health/readiness"
printf '\nHealth validation succeeded.\n'
