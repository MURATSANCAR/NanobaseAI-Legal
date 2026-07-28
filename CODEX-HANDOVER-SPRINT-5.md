# CODEX Handover — Sprint 5

## 1. Sprint 4 ön koşul doğrulaması

V10 analysis profile, ontology/version, terminology, policy/version, prompt/schema,
requirements/revisions/sources, extraction jobs, expert feedback, model registry/
routing, grounding/confidence ve requirement-grid altyapısı doğrulandı. Ayrıntılı
matris `docs/SPRINT-5-PREREQUISITE-CHECK.md` dosyasındadır.

## 2. Yapılan değişiklikler

Dinamik knowledge graph, evidence provenance/validity, knowledge extraction job,
snapshot-scoped compliance engine, strategy/reranking/confidence portları, review/
feedback API'leri, Rabbit/outbox/SSE, observability ve dinamik frontend workspace'i
eklendi. Sprint 4 davranışları korunmuştur.

## 3. Dynamic entity modeli

`knowledge_entity` generic entity'dir. Type aktif ontology UUID'sidir; firma/ürün
Java sınıfı veya enum yoktur. Status/geçerlilik/version platform lifecycle alanlarıdır.

## 4. Attribute yapısı

`entity_attribute` concept + genişleyebilir typed value zarfı + unit concept + gerçek
source fragment taşır. Bilinmeyen value type unsupported metadata olarak saklanır.

## 5. Relation yapısı

`knowledge_relation` iki generic entity, dynamic relation concept, JSON attributes,
confidence, validity ve evidence kaynağı taşır. Tenant composite FK uygulanır.

## 6. Capability modeli

`capability` owner entity ve capability concept'ine; `capability_evidence` ise fragment,
dynamic evidence role ve strength'e bağlıdır.

## 7. Knowledge extraction

Document role concept'li `knowledge_extraction_profile` snapshot oluşturulur. Clause'lar
evidence fragment'e çevrilir, validity değerlendirilir, yalnız seçili fragment +
ontology/schema local orchestrator'a gider. Unknown concept candidate olur.

## 8. Evidence domain

Fragment; document/version/clause/page/text/bbox/offset/hash/quality/geçerlilik taşır.
Claim; subject/predicate/object veya dynamic value'yu fragment'e grounded olarak
bağlar. Usages API provenance tüketicilerini listeler.

## 9. Evidence validity

Policy motoru not-expired, parser/OCR, verification ve authority sinyallerini
versioned JSON ağırlıklarıyla hesaplar. Selector ontology status concept'ine çözülür.
Verify/invalidate append assessment, audit ve outbox üretir.

## 10. Source authority

Global/tenant policy, source type score, issuer override ve
`source_authority_profile` override desteklenir. Güven sırası Java'da sabit değildir.

## 11. Entity resolution

Port implementasyonu normalize isim, identifier, manufacturer, model/version ve
historical sinyalleri policy ile skorlar. Possible/ambiguous otomatik merge edilmez.

## 12. Knowledge snapshot

Compliance job entity/evidence cutoff, ontology, terminology, policy versions ve hash
taşıyan `knowledge_snapshot` oluşturur. Evaluation snapshot FK'sini saklar.

## 13. Retrieval pipeline

Tenant/snapshot → entity scope → concept/attribute/typed filter → full-text →
relation expansion → validity/authority/history → policy limit akışı uygulanır.
Pgvector provider henüz bağlı değildir.

## 14. Reranking

`EvidenceReranker` port'u ontology, lexical, attribute, validity, authority, history,
relation distance ve freshness sinyallerini policy ağırlıklarıyla top-K yapar.

## 15. Comparison strategies

Registry/port üzerinden numeric threshold, numeric range, date validity ve boolean
existence provider'ları vardır. Birim dönüşümü measurement catalog metadata'sından
gelir. Desteklenmeyen alan semantic/manual yola gider.

## 16. Composite requirement değerlendirmesi

`compliance_condition` parent/child concept ağacını saklar.
`CompositeConditionEvaluator` ALL/ANY/NOT/AT_LEAST_N birleşimini deterministic yapar.
Otomatik condition extraction ayrı job olarak tamamlanmamıştır.

## 17. Compliance engine

Requirement task başına retrieval/rerank/deterministic-or-semantic değerlendirme,
dynamic decision concept, evidence link, confidence, progress ve review event'i
üretir. Retry failed aynı snapshot'ı kullanır.

## 18. Confidence sistemi

Score, seviye, review threshold, weight ve penalty'ler aktif policy'dendir. Factor
effect'leri explanation JSON'da tutulur; contradiction review'u zorlar.

## 19. Expert feedback

Review, final decision/revision/audit ve `expert_feedback` snapshot'ı yazar. Feedback
concept'i ontology'dendir ve `approved_for_learning=false`; production policy'yi
otomatik değiştirmez.

## 20. Frontend ekranları

Knowledge Center, dinamik entity profile, attributes/capabilities/relations/history,
evidence/PDF viewer; Compliance Workspace, üç panelli çalışma alanı ve backend-config
matrix eklendi. Decision seçenekleri API'den gelir.

## 21. Yeni API'ler

Knowledge entity/attribute/relation/capability CRUD + merge/split/revision; evidence
list/detail/verify/invalidate/usages; knowledge extraction job/SSE/cancel; compliance
job/SSE/cancel/retry ve evaluation list/detail/review/evidence/history/explanation.
Tam liste `docs/COMPLIANCE-API.md` içindedir.

## 22. Yeni migration'lar

`V11__dynamic_knowledge_compliance.sql`: knowledge, evidence, retrieval/comparison,
compliance, snapshot, revision, dynamic seed catalog/policy/prompt/UI config, index,
composite FK ve FORCE RLS. Ardından mevcut `V12__dynamic_risk_conflict_impact.sql`
uyumluluğu korunmuştur.

## 23. RabbitMQ event'leri

Knowledge requested/started/completed/failed; entity created/updated/merged;
capability created; evidence verified/invalidated; compliance requested/started/
completed/failed/review-required; expert feedback recorded outbox routing key'leri
mevcuttur. Progress canlı SSE + persisted event tablosundadır.

## 24. Çalıştırılan komutlar

```text
JAVA_HOME=<JDK21> /tmp/codex-maven/bin/mvn -q test
PYTHONPATH=<temporary-requirements> python3 -m pytest -q
python3 evaluation/evaluate_sprint5.py
pnpm test
pnpm run build
git diff --check
```

## 25. Backend test sonuçları

71 test geçti; 0 failure/error. Sprint 5 engine suite 8, ArchUnit kuralları dahil.
Testcontainers canlı entegrasyonu Docker yokluğu nedeniyle çalıştırılamadı.

## 26. Frontend test sonuçları

13 test geçti; Vinext production build başarılı.

## 27. Evaluation sonuçları

15 contract-golden case: decision accuracy 1.00, grounding coverage 0.8667,
deterministic rate 0.80, LLM rate 0.20, manual-review rate 0.80. Sentetik set gerçek
model benchmark'ı değildir.

## 28. Performans ölçümleri

Sentetik golden sette ortalama 28.87 ms ve 1,385 toplam semantic token kaydı vardır.
Production metrikleri: knowledge/evidence sayıları, compliance deterministic/LLM/
review/missing/contradiction, retrieval candidate/duration, reranking duration ve
comparison strategy. Gerçek yük testi yapılmamıştır.

## 29. Güvenlik eksikleri

KVKK/sensitive attribute için backend field-level masking enforcement, multi-replica
SSE fan-out ve signed-in browser E2E eksiktir. RLS canlı testi Docker hostuna kalmıştır.

## 30. Tamamlanamayan alanlar

Üretim pgvector/embedding provider'ı, automatic governed condition-tree extraction,
gerçek müşteri/model evaluation baseline'ı ve canlı PostgreSQL/Rabbit/MinIO
Testcontainers doğrulaması tamamlanmamıştır. Sistem bunlar tamamlanmış gibi
raporlanmamıştır.

## 31. Sonraki sprint önerisi

Önce CI'da PostgreSQL 17/Flyway/RLS ve broker E2E gate'i zorunlu yapın. Sonra tenant
embedding provider + pgvector indeksini, condition extraction job'ını, field-level
redaction'ı ve müşteri-onaylı evaluation setini candidate policy activation akışına
bağlayın.
