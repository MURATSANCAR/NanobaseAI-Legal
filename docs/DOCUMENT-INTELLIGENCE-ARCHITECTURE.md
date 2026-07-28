# Document Intelligence Mimarisi

Akış:

```text
Upload → specai-temp → hash/size doğrulama → specai-original
      → PostgreSQL document/version/job/outbox
      → RabbitMQ document.processing.requested.v1
      → idempotent consumer → parser router
      → Docling veya OpenContracts adapter
      → provider-neutral extraction result
      → page/clause/table/warning persistence
      → processing_event → SSE + polling
```

`DocumentIntelligencePort` sağlayıcıdan bağımsız `submit`, `getStatus`, `getResult`
ve `cancel` sözleşmesidir. Domain katmanı HTTP/GraphQL veya provider SDK tipi
taşımaz. `RoutedDocumentIntelligenceAdapter`, MIME/uzantı, OCR sinyali,
sağlayıcı erişilebilirliği ve annotation isteğine göre route eder.

Provider sonuçları yalnız normalize koordinatlarla saklanır. Ham provider
koordinatları metadata içinde taşınabilir. Reprocess aynı document version için
yeni `document_processing_job` üretir; eski job/event geçmişi silinmez.

## Güven sınırları

- Organization, doğrulanmış JWT `tenant_id` claim’inden alınır.
- Worker organization değerini event envelope’dan alır ve object key prefix’iyle
  karşılaştırır.
- Storage credentials mesaj/request body’sine girmez.
- Parser exception/stack trace kullanıcı event’ine yazılmaz.
- Doküman metni uygulama loglarına yazılmaz.

