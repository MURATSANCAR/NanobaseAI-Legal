# CODEX Handover — Sprint 6

## 1. Sprint 4–5 ön koşul doğrulaması

Sprint 4 dynamic requirement bileşenleri korundu. Eksik Sprint 5 knowledge,
evidence ve compliance temeli V11 ile tamamlandı. Bileşen bazlı kanıt:
`docs/SPRINT-6-PREREQUISITE-CHECK.md`.

## 2. Yapılan değişiklikler

Dinamik risk/ambiguity/conflict/change-impact veri modeli, policy motorları,
job/event/consumer akışı, review/revision/audit/feedback API’leri, AI guard
sözleşmeleri, frontend çalışma alanları, test ve dokümanlar eklendi.

## 3. Dynamic risk taxonomy

`risk_taxonomy` ve immutable sürümleri ontology version’a bağlıdır. Business
etiketleri enum değildir; tenant/global resolution `RiskCatalogPort` arkasındadır.

## 4. Risk signal engine

`ConfigurableRiskSignalEngine`, policy weights, transforms, thresholds ve
concept mappings kullanır. Requirement grounding/confidence/testability ve
valid evidence sinyalleri ilk provider girdileridir.

## 5. Probability/impact/exposure motoru

`ConfigurableRiskExposurePolicyEngine`, provider registry üzerinden
weighted/hybrid ve matrix yöntemlerini çalıştırır. Factor effect ve dynamic
severity concept döndürür. `ConfigurableRiskConfidencePolicyEngine` ise
source-confidence, grounding ve signal girdilerini ayrı versioned policy ile
hesaplayıp açıklama factor’larıyla birlikte saklar.

## 6. Ambiguity engine

Structured JSON pointer feature extraction + policy threshold uygulanır.
Finding, source ve interpretations ayrı saklanır.

## 7. Conflict candidate generation

SQL tenant/project/concept/version filtresi ardından scope/concept/attribute/
version rerank ve limit uygulanır; all-pairs yoktur.

## 8. Document authority policy

Versioned policy tablosu eklendi. Global default kesin öncelik tanımlamaz.
AI guard, rule yokken preferred source üretimini reddeder.

## 9. Conflict comparison strategies

Registry ve `StructuredValueConflictStrategy` eklendi. Policy JSON pointer ve
tolerance kullanır; iki grounded source zorunludur.

## 10. Requirement dependency graph

Ontology concept tabanlı `requirement_dependency` ve clause→requirement→
evaluation/risk graph adapter’ı eklendi.

## 11. Document version ve addendum analizi

Yapısal matcher clause number, hash, token similarity ve policy threshold
kullanır; addendum üstünlüğü varsaymaz.

## 12. Change set yapısı

`document_change_set/item`, correction API ve audit akışı vardır. Eski sürüm
silinmez.

## 13. Impact analysis

Bounded/confidence-aware BFS yalnız graph ile etkilenen entity’leri üretir ve
job/result/event olarak saklar.

## 14. Staleness yönetimi

Etkilenen persisted analizler ontology status/trigger ile stale kaydı alır.
Risk merkezi açık stale durumunu gösterir.

## 15. Risk propagation

Path ve confidence taşıyan `PROPAGATED_RISK_CANDIDATE` kayıtları oluşturulur;
otomatik final risk oluşmaz.

## 16. Mitigation sistemi

Versioned catalog/pattern/candidate modeli ve uzman review endpoint’i eklendi.
Bootstrap katalog boştur ve otomatik task üretmez.

## 17. Clarification candidate sistemi

Versioned strategy, persistence ve guarded AI endpoint vardır. Unknown source,
unknown concept ve authority uydurma reddedilir; candidate dışarı gönderilmez.

## 18. Frontend ekranları

Risk merkezi, risk detay, üç panelli conflict, ambiguity ve change-impact
workspace gerçek API’lere bağlıdır. Kaynak PDF sayfasına açılır.

## 19. Yeni API’ler

Tam liste `docs/RISK-API.md` içindedir. Risk analysis, record review, ambiguity,
conflict, change set ve impact endpoint’leri `/api/v1` altındadır.

## 20. Yeni migration’lar

- `V11__dynamic_knowledge_compliance.sql`
- `V12__dynamic_risk_conflict_impact.sql`

Her yeni tenant tablosunda RLS enable + force uygulanır.

## 21. RabbitMQ event’leri

Risk request/start/progress/complete/fail routing key’leri ve durable
`risk-analysis.request` kuyruğu eklendi. Consumer
`ConsumerIdempotencyService` ile claim/complete/failed uygular. Change/impact/
stale/clarification/mitigation routing adları tanımlıdır; ilgili transaction’lar
outbox publish kullanır.

## 22. Çalıştırılan komutlar

- `mvn test`
- `mvn verify`
- `pnpm run test`
- `pytest -q`
- `python evaluation/evaluate_sprint6.py`

## 23. Backend test sonuçları

71 test başarılı; failure/error yok. Architecture testleri fixed taxonomy,
controller scoring ve port sınırlarını kapsar.

## 24. Frontend test sonuçları

Vinext production build ve 13 Node source-contract testi başarılıdır.

Repository genelinde 9 Python testi de başarılıdır.

## 25. Evaluation sonuçları

19 sentetik contract-golden case: risk/conflict/ambiguity precision-recall 1.0,
grounding/authority/change/impact/staleness accuracy 1.0, Brier 0.06625.
Production model metriği değildir.

## 26. Performans ölçümleri

Golden sette ortalama 16.53 ms, deterministic çözüm oranı %89.47, LLM çağrı
oranı %10.53, 770 token. Büyük proje/pgvector load testi yapılmadı.

## 27. Güvenlik eksikleri

Risk source redaction adapter’ı ve auditli export endpoint’i eksiktir. Canlı RLS
ve signed URL E2E Docker/browser ortamı gerektirir.

## 28. Tamamlanamayan alanlar

Date/range/composite conflict provider’ları, shared evidence/capability/task/
report graph adapter’ları, portal clarification edit formu ve görsel clause
eşleştirme seçici tamamlanmadı. Detay `docs/KNOWN-ISSUES.md`.

## 29. Sonraki sprint önerisi

Müşteri dataset’i ve lokal modelle gerçek evaluation baseline oluşturun; ardından
semantic conflict provider, pgvector retrieval load testi, redaction/export ve
browser E2E’yi quality gate’e bağlayın.
