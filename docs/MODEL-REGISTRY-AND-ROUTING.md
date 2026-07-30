# Model Registry ve Routing

Registry `model_definition`, `model_deployment`, `model_profile` tablolarındadır.
Profile-to-deployment ilişkisi `selection_policy_json.deploymentIds` ile yönetilir.
Profile kodları serbesttir; FAST/QUALITY özel enum değildir.

`PolicyModelRoutingEngine` routing policy'deki profile base score ve signal weights ile
adayları puanlar. Strategy'nin istediği profile, deployment sağlık bilgisi ve policy
quality/latency sinyalleri karara girebilir. Karar `model_routing_decision` tablosunda
policy version, reason codes ve signal snapshot ile saklanır.

Backend'in dış model adı daima `nanobase-spec-ai`'dır. FastAPI AI Orchestrator,
`MODEL_DEPLOYMENTS_JSON` içinden profile karşılık gelen OpenAI-compatible runtime'ı
seçer. Runtime model adı, base URL ve credential UI'a veya requirement açıklamasına
dönmez. Orchestrator tool, internet ve filesystem erişimi sağlamaz.

## Compliance semantic routing

Compliance semantic evaluation `ComplianceSemanticRouter` ile yönetilir:

| Mode | Canlı karar | FAST |
|------|-------------|------|
| `BALANCED_ONLY` | BALANCED | yok |
| `SHADOW` | BALANCED | paralel kayıt / karşılaştırma |
| `LIVE_FAST` | FAST; düşük güven / çelişki / çok kanıt / FAST failure → BALANCED | birincil |

Önerilen FAST runtime (ayrı deployment, ürün adı dışarı sızmaz): `reasoning=false`,
`temperature=0`, `topP=0.8`, `maxTokens=512`, `timeoutSeconds=300`.
Shadow kapıları ve rollback: `docs/runbooks/RUNBOOK-COMPLIANCE-FAST-SHADOW.md`.
