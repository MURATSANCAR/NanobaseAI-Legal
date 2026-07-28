#!/usr/bin/env bash
set -euo pipefail

failures=0
minimum_cpu="${MINIMUM_CPU_CORES:-8}"
minimum_ram_gb="${MINIMUM_RAM_GB:-16}"
minimum_disk_gb="${MINIMUM_DISK_GB:-100}"

check() {
  local label="$1"
  shift
  if "$@" >/dev/null 2>&1; then
    printf 'OK   %s\n' "$label"
  else
    printf 'FAIL %s\n' "$label"
    failures=$((failures + 1))
  fi
}

cpu_count="$(getconf _NPROCESSORS_ONLN 2>/dev/null || sysctl -n hw.ncpu)"
ram_bytes="$(getconf _PHYS_PAGES 2>/dev/null || echo 0)"
page_size="$(getconf PAGE_SIZE 2>/dev/null || echo 0)"
ram_gb=$((ram_bytes * page_size / 1024 / 1024 / 1024))
disk_gb="$(df -Pk "${INSTALLATION_ROOT:-.}" | awk 'NR==2 {print int($4/1024/1024)}')"

if (( cpu_count >= minimum_cpu )); then printf 'OK   CPU cores: %s\n' "$cpu_count"; else
  printf 'FAIL CPU cores: %s (minimum %s)\n' "$cpu_count" "$minimum_cpu"; failures=$((failures + 1))
fi
if (( ram_gb >= minimum_ram_gb )); then printf 'OK   RAM GiB: %s\n' "$ram_gb"; else
  printf 'FAIL RAM GiB: %s (minimum %s)\n' "$ram_gb" "$minimum_ram_gb"; failures=$((failures + 1))
fi
if (( disk_gb >= minimum_disk_gb )); then printf 'OK   free disk GiB: %s\n' "$disk_gb"; else
  printf 'FAIL free disk GiB: %s (minimum %s)\n' "$disk_gb" "$minimum_disk_gb"; failures=$((failures + 1))
fi

check "Docker engine" docker info
check "Docker Compose" docker compose version
check "Clock synchronization" sh -c 'command -v timedatectl >/dev/null && timedatectl show -p NTPSynchronized --value | grep -q true || command -v sntp >/dev/null'

if command -v nvidia-smi >/dev/null; then
  check "GPU driver" nvidia-smi
else
  printf 'INFO GPU not detected; CPU model runtime profile is required\n'
fi

for variable in DATABASE_HOST MINIO_HOST RABBITMQ_HOST REDIS_HOST KEYCLOAK_HOST MODEL_RUNTIME_HOST; do
  value="${!variable:-}"
  if [[ -n "$value" ]]; then
    check "DNS ${variable}" getent hosts "$value"
  else
    printf 'INFO %s not configured; connectivity check skipped\n' "$variable"
  fi
done

for variable in TLS_CERTIFICATE_FILE MODEL_FILE BACKUP_TARGET; do
  value="${!variable:-}"
  if [[ -n "$value" && -e "$value" ]]; then
    printf 'OK   %s\n' "$variable"
  else
    printf 'FAIL %s is missing or unreadable\n' "$variable"
    failures=$((failures + 1))
  fi
done

if (( failures > 0 )); then
  printf '\nInstallation validation failed with %s blocking finding(s).\n' "$failures"
  exit 1
fi
printf '\nInstallation prerequisites are satisfied.\n'
