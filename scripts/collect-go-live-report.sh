#!/usr/bin/env bash
# Collect go-live day package placeholders and local verification outputs.
# Usage: scripts/collect-go-live-report.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${REPORT_DIR:-$ROOT/release/2026-07-31}"
mkdir -p "$OUT"

git_sha="$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo UNKNOWN)"
java_ver="$(java -version 2>&1 | head -1 || true)"
mvn_ver="$(mvn -version 2>/dev/null | head -1 || true)"

cat > "$OUT/release-manifest.txt" <<EOF
Release version: 2026.07.31-rc1
Git SHA: ${git_sha}
Backend image: backend:2026.07.31-rc1
UI image: ui:2026.07.31-rc1
Orchestrator image: orchestrator:2026.07.31-rc1
Migration range: V19-V26
JDK: ${java_ver}
Maven: ${mvn_ver}
Test count: see test-report.txt
Expected feature flags day-1: V2, CLASSIFICATION, CAPABILITY, DETERMINISTIC, GAP
Day-1 disabled: CLARIFICATION, RISK, BID, OBLIGATION
Pilot organization ID: REPLACE_WITH_PILOT_ORG_UUID
Previous backend image: REPLACE
Previous UI image: REPLACE
Previous orchestrator image: REPLACE
EOF

cat > "$OUT/production.env.example" <<'EOF'
COMPLIANCE_ROUTING_MODE=BALANCED_ONLY
COMPLIANCE_FAST_ENABLED=false
COMPLIANCE_SHADOW_ENABLED=false
COMPLIANCE_EVALUATION_PARALLELISM=1
AI_ORCHESTRATOR_CONNECT_TIMEOUT=PT5S
AI_ORCHESTRATOR_COMPLIANCE_READ_TIMEOUT=PT780S
AI_ORCHESTRATOR_COMPLIANCE_GLOBAL_DEADLINE=PT820S
TENDER_DOMAIN_V2_ENABLED=false
REQUIREMENT_CLASSIFICATION_ENABLED=false
COMPANY_CAPABILITY_REGISTRY_ENABLED=false
DETERMINISTIC_EVALUATION_ENABLED=false
GAP_ANALYSIS_ENABLED=false
CLARIFICATION_MANAGEMENT_ENABLED=false
RISK_ENGINE_ENABLED=false
BID_DECISION_ENABLED=false
OBLIGATION_MANAGEMENT_ENABLED=false
EOF

cat > "$OUT/rollback-runbook.txt" <<'EOF'
1. Set all TENDER_* / REQUIREMENT_* / COMPANY_* / DETERMINISTIC_* / GAP_* env flags to false
2. Restart backend
3. Confirm production_runtime_policy JSON shows intelligenceEnv all false
4. Run one baseline compliance smoke job (flags off)
5. Optional: run scripts/pilot-disable-tender-intelligence.sql
6. Image rollback only if baseline compliance itself is broken
7. Never DROP V19–V26 tables for rollback
EOF

cat > "$OUT/go-live-checklist.txt" <<'EOF'
[ ] Release images pinned (not latest)
[ ] Staging V19–V26 SUCCESS
[ ] Full test suite PASS
[ ] FeatureGateIntegrationTest PASS
[ ] Baseline compliance smoke PASS (all flags off)
[ ] Tenant isolation PASS
[ ] Pilot enable/disable scripts verified
[ ] Three acceptance jobs PASS
[ ] Rollback drill PASS
[ ] Monitoring counters visible
[ ] GO / HOLD decision recorded
EOF

touch "$OUT/migration-report.txt"
touch "$OUT/acceptance-jobs-report.txt"
touch "$OUT/tenant-isolation-report.txt"

if [[ -f "$OUT/test-report.txt" ]]; then
  printf 'Existing test-report.txt preserved\n'
else
  printf 'PENDING — run mvn test and paste summary\n' > "$OUT/test-report.txt"
fi

printf 'Go-live package written to %s\n' "$OUT"
ls -1 "$OUT"
