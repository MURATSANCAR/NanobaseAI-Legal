# Workflow Tanımı

Ana tablolar V13 migration’ındadır:

- `workflow_definition`: tenant/scope ve aktif sürüm işaretçisi.
- `workflow_version`: DRAFT/TESTING/ACTIVE/RETIRED lifecycle.
- `workflow_node`: ontology `node_type_concept_id` ve JSON configuration.
- `workflow_transition`: concept türü, güvenli condition, authorization/action JSON.

Tanım ve sürüm ayrı tutulur. Aktif sürüm yerinde değiştirilmez; değişiklik yeni draft
sürümle yapılır. Aktivasyon öncesi simulation sonucu geçerli olmalıdır.

Asgari graph doğrulamaları: tek veya açıkça işaretli entry, node-code benzersizliği,
transition referanslarının varlığı, dead-end, erişilemeyen node, döngü visit limiti,
terminal/finalization yolu ve yetkilendirilmeyen node kontrolüdür.

REST:

- `GET/POST /api/v1/workflows`
- `POST /api/v1/workflows/{id}/versions`
- `POST /api/v1/workflow-versions/{id}/simulate`
- `POST /api/v1/workflow-versions/{id}/activate`

Scope değerleri platform lifecycle metadata’sıdır; görev, karar, rol ve node iş
semantiği concept/configuration verisinden gelir.
