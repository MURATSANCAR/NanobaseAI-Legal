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
