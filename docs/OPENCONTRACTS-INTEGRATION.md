# OpenContracts Entegrasyonu

Adapter: `OpenContractsDocumentIntelligenceAdapter`.

Davranış:

1. Organization + document version + provider mapping’i aranır.
2. Corpus ID yoksa proje için corpus oluşturulur.
3. Doküman corpus’a idempotent correlation bilgisiyle gönderilir.
4. External corpus/document/version ID’leri `external_document_mapping` içinde tutulur.
5. Status/result provider-neutral modellere çevrilir.
6. Circuit breaker ardışık provider hatalarında açılır.

`OPENCONTRACTS_ENABLED=false` varsayılandır. Base URL ve token secret/environment
üzerinden verilir. OpenContracts readiness’i ana uygulamanın readiness’ini düşürmez.
Gerçek deployment’ın endpoint/schema sözleşmesi contract test fixture’larıyla
eşleştirilmeden production’da etkinleştirilmemelidir.

Bu repository OpenContracts container’ı paketlemez; dolayısıyla `latest` veya
doğrulanmamış bir image eklenmemiştir.

