# Dinamik Policy Motoru

`policy_definition` semantik kimliği, `policy_version.configuration_json` davranışı
tutar. Extraction, routing ve confidence ayrı sürümlerdir. `PolicyConfiguration`
zorunlu değer eksikse fail-closed davranır; görünmez Java default eşiği kullanmaz.

Mevcut configuration alanları:

- clause signal weights/decision thresholds,
- document complexity levels,
- context relevance weights/limit,
- prompt ontology shortlist limit,
- extraction strategies,
- grounding excluded paths,
- duplicate weights/thresholds,
- model profile score rules,
- confidence weights/levels/review boundary.

Global baseline bir çalıştırılabilir başlangıçtır, sektör taksonomisi değildir.
Tenant policy'si global kaydın önüne geçer. Aday sürüm doğrudan production'a geçmez;
evaluation run'daki minimum/maximum quality gate'ler ve yönetici onayı gerekir.
