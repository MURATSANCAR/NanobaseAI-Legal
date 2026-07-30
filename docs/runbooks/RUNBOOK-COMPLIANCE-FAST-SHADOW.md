# Compliance FAST Shadow / Promotion

**Belirti:** FAST shadow disagreement, false COMPLIANT, veya structured
JSON failure artışı. **Neden:** FAST runtime down, prompt/schema uyumsuzluğu,
reasoning açık kalması, veya kalite regresyonu.

## Kontroller

1. `COMPLIANCE_ROUTING_MODE` (`SHADOW` | `LIVE_FAST` | `BALANCED_ONLY`)
2. Orchestrator `MODEL_DEPLOYMENTS_JSON` içinde `profile=FAST` ve `BALANCED`
3. FAST runtime health (`baseUrl` / vLLM) ve `reasoning=false`
4. Metrikler:
   - `compliance_shadow_agreement_total` / `compliance_shadow_total`
   - `compliance_false_compliant_total` (=0 olmalı)
   - `compliance_structured_json_failure_total{profile="FAST"}`
   - `compliance_llm_latency_seconds{profile="FAST"}` p95
5. DB: `compliance_evaluation.shadow_comparison_json`

## Shadow → LIVE_FAST geçiş kapıları

Aynı dönemde:

| Gate | Eşik |
|------|------|
| Structured JSON success | ≥ %99 |
| Yanlış COMPLIANT (`compliance_false_compliant_total`) | = 0 |
| FAST/BALANCED karar uyumu | ≥ %95 |
| FAST p95 latency | mevcut BALANCED p95’in en fazla %50’si |

## Müdahale

1. Anında: `COMPLIANCE_ROUTING_MODE=BALANCED_ONLY` (canlı karar BALANCED)
2. FAST recovery sonrası tekrar `SHADOW`
3. Gate’ler geçince `LIVE_FAST` (escalation: düşük güven / çelişki / çok kanıt / FAST failure → BALANCED)

## Veri riski

Shadow canlı kararı değiştirmez. LIVE_FAST’te yanlış COMPLIANT riski için
`compliance_false_compliant_total` ve grounded validation fail-closed kalır.

## Eskalasyon

AI/SRE; model snapshot rollback quality gate’e tabidir.
