#!/usr/bin/env bash
set -euo pipefail

failures=0
reject() {
  local description="$1"
  local pattern="$2"
  shift 2
  if rg -n "$pattern" "$@" >/dev/null; then
    printf 'FAIL %s\n' "$description"
    rg -n "$pattern" "$@"
    failures=$((failures + 1))
  else
    printf 'OK   %s\n' "$description"
  fi
}

reject "production images do not use latest" 'image:\s*\S+:latest\b' \
  compose*.yaml Dockerfile services/*/Dockerfile frontend/Dockerfile
reject "repository does not contain private keys" 'BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY' .
reject "model runtime is not host-published" '8092:8090' compose*.yaml
secret_findings="$(rg -n '(PASSWORD|SECRET|TOKEN):' \
  src/main/resources/application-production.yml compose.production.yaml \
  | rg -v ':[0-9]+:\s*[A-Z0-9_]*(PASSWORD|SECRET|TOKEN):\s*(null|\$\{|$)' || true)"
if [[ -n "$secret_findings" ]]; then
  printf 'FAIL production config has no default secret\n%s\n' "$secret_findings"
  failures=$((failures + 1))
else
  printf 'OK   production config has no default secret\n'
fi

if rg -n '^USER (root|0)$' Dockerfile services/*/Dockerfile frontend/Dockerfile >/dev/null; then
  printf 'FAIL production Dockerfile declares root user\n'
  failures=$((failures + 1))
else
  printf 'OK   production Dockerfiles do not declare root runtime users\n'
fi

if (( failures > 0 )); then exit 1; fi
