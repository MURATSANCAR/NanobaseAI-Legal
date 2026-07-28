# CODEX Handover — Sprint 4

## 1. Mevcut yapı doğrulaması

Sprint 1–3 modüler monolit, Spring Boot 3.4/Java 21, PostgreSQL/Flyway, RabbitMQ outbox,
MinIO, document parser adapters, tenant transaction/RLS ve React portal korunmuştur.
Yeni geliştirme `analysis` bounded context'i ve ayrı AI Orchestrator servisidir.

## 2. Yapılan değişiklikler

V10 dynamic schema, analysis Java sınıfları/sözleşmeleri, idempotent extraction consumer,
REST/SSE API, dynamic matrix UI, FastAPI local runtime gateway, unit/architecture/frontend
testleri ve Sprint 4 belgeleri eklendi.

## 3. Kaldırılan hardcoded analiz mantıkları

Yeni modülde sector category enum, modality kelime listesi, unit enum, sentence regex
zinciri, fixed confidence formülü, fixed routing threshold veya FAST/QUALITY switch yoktur.
Var olan document lifecycle enumları platform kuralı olarak korunmuştur.

## 4. Dynamic ontology yapısı

Ontology/version/concept/relation type/relation tabloları, ACTIVE version çözümü ve GET
API'leri eklendi. Global baseline yalnız extensible root içerir.

## 5. Terminoloji kataloğu

Tenant/global catalog çözümü, weighted relevant matching ve candidate -> approve/reject
akışı eklendi. Model adayı otomatik active olmaz.

## 6. Analysis profile yapısı

Her job öncesi immutable snapshot ve SHA-256 hash üretilir. Ontology, terminology,
extraction/routing/confidence policy, prompt ve schema version kimlikleri saklanır.

## 7. Clause signal motoru

Approved terminology, structural parser context ve numeric signal policy weights ile
birleştirilir. Eşikler DB JSON'undan gelir.

## 8. Dinamik context seçimi

Clause/table adayları lexical, structural ve page relevance ile policy limitine göre
seçilir. Sabit sibling penceresi yoktur.

## 9. Extraction strategy sistemi

Policy koşulları code/profile/table/token/validation/retry/second-validator alanlarını
dinamik çözer. Yeni strategy kod değişikliği gerektirmez.

## 10. Model registry

Definition/deployment/profile tabloları ve private deployment selection metadata eklendi.

## 11. Model routing

Generic signal score policy engine, health filtresi, fallback-ready profile verisi ve
kalıcı routing decision/reason snapshot vardır.

## 12. Prompt package yapısı

Versioned components + output schema bağlantısı vardır. Relevant terminology, profile
metadata ve ontology shortlist runtime'da assemble edilir.

## 13. Output schema yönetimi

Versioned JSON Schema fail-closed doğrulanır. Sector fields `attributes` içinde genişler.

## 14. Requirement modeli

Dynamic attributes, concept/modality reference, grounding/confidence, version IDs,
source fragment, model run, review status ve optimistic locking eklendi.

## 15. Grounding doğrulaması

Exact, normalized, numeric, catalog unit ve page/bounding-box katmanları uygulanır.
Ungrounded aday persist edilmez; partial sonuç onaylanamaz.

## 16. Confidence politikası

Factor/weight/level/review boundary tamamen policy verisidir ve açıklama JSON'unda saklanır.

## 17. Uzman feedback sistemi

Edit/review/split/merge feedback snapshot ve revision üretir. Learning approval production
policy'yi otomatik değiştirmez.

## 18. Evaluation sonuçları

Generic metric aggregation ve minimum/maximum quality gate unit testleri başarılıdır.
Gerçek lokal model evaluation sonucu, onaylı müşteri dataset'i bulunmadığı için yoktur.

## 19. Backend test sonuçları

`mvn verify`: 49 unit/architecture testi başarılı, failure/error yok. Docker bulunmadığı
için 5 Testcontainers integration testi skip edildi. Sprint 4 engine ve architecture
testleri suite'e dahildir.

## 20. Frontend test sonuçları

Lint başarılıdır. Production Vinext build ve 8 Node contract testi başarılıdır. Dynamic
grid endpoint, extraction ve explanation bağlantıları test edilir. Standalone TypeScript
kontrolü mevcut Cloudflare worker ambient type eksikleri nedeniyle çalışmaz; bu Sprint 4
UI koduna özgü değildir.

AI Orchestrator için geçici virtualenv üzerinde 3 FastAPI contract kontrolü
(liveness, readiness ve model deployment yokken fail-closed 503) başarılıdır.

## 21. Çalıştırılan komutlar

```text
mvn clean test -DskipTests
mvn test
pnpm lint
pnpm test
python -m compileall -q services/ai-orchestrator
```

JDK/Maven sistem PATH'inde olmadığı için `.cache/codex-java` ve `.cache/codex-maven`
binary yolları kullanılmıştır.

## 22. Tamamlanamayan alanlar

Onaylı production evaluation dataset'i ve gerçek model baseline/candidate koşusu yoktur.
Embedding/classifier signal provider ile semantic/table/OCR-alternate grounding adapter'ları
henüz bağlı değildir.

## 23. Güvenlik eksikleri

Prompt injection authority ayrımı, no-tools, offline runtime, schema/source enforcement,
RLS ve log redaction uygulanmıştır. Multi-instance SSE fan-out için shared broker gerekir.

## 24. Performans ölçümleri

Model runtime yapılandırılmadığından model latency/token benchmark yapılmadı. Her model run
latency/token alanlarını ve routing decision'ı saklayacak şekilde hazırdır.

## 25. Sonraki sprint önerisi

Onaylı çok-sektörlü dataset oluşturun; embedding/classifier ve semantic/table grounding
adapter'larını ekleyin; baseline/candidate evaluation çalıştırın; quality gate'i geçen
policy/prompt/ontology sürümünü yönetici workflow'u ile aktive edin.
