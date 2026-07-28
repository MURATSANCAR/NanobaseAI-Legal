# Dinamik Analiz Mimarisi

Sprint 4, mevcut doküman işleme modüllerini değiştirmeden `analysis` bounded context'ini
ekler. Business backend yalnız `AiGateway.LOGICAL_MODEL = nanobase-spec-ai` adını bilir.
Runtime/model ailesi seçimi lokal AI Orchestrator sınırının arkasındadır.

```text
READY document
  -> immutable AnalysisProfile
  -> requirement.extraction.requested.v1
  -> hybrid ClauseSignalEvaluator
  -> relevance based ClauseContextBuilder
  -> policy based ExtractionStrategyResolver
  -> policy based ModelRoutingEngine
  -> local AI Orchestrator
  -> dynamic JSON Schema validation
  -> layered grounding + unit resolution
  -> explainable confidence + duplicate assessment
  -> Requirement + source fragments + revision
  -> expert review/feedback
```

Analiz taksonomisi, terimler, eşikler, strateji adları, model profilleri ve confidence
ağırlıkları Java enumlarında tutulmaz. Bunlar V10 şemasındaki versiyonlu kayıtlardan
çözülür. Sabit kalan alanlar tenant izolasyonu, event idempotency, kaynak zorunluluğu,
şema doğrulaması, audit/revision ve insan onayı gibi platform güvenlik kurallarıdır.

## Modül sınırları

- `analysis.domain`: immutable profil snapshot'ı, job, requirement ve revision.
- `analysis.application`: portlar ve policy-driven saf motorlar.
- `analysis.infrastructure`: JDBC katalog adaptörü, kalıcılık ve HTTP AI gateway.
- `analysis.integration`: RabbitMQ envelope/consumer.
- `analysis.api`: entity döndürmeyen REST/SSE sözleşmeleri.
- `services/ai-orchestrator`: runtime kimliğini ve credential'ları izole eden FastAPI.

DB RLS, tenant kayıtlarını `app.current_organization_id()` ile sınırlar. Global baseline
kayıtları okunabilir; tenant transaction'ı global kayıtları değiştiremez.
