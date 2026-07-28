# OpenContracts Entegrasyonu

Adapter: `OpenContractsDocumentIntelligenceAdapter`.

Proje içi facade servisi: `services/opencontracts` (Docling üzerine OpenContracts
API yüzeyi). Compose servis adı: `opencontracts` (`:8091`).

Davranış:

1. Organization + document version + provider mapping’i aranır.
2. Corpus ID yoksa proje için corpus oluşturulur.
3. Doküman corpus’a idempotent correlation bilgisiyle gönderilir.
4. External corpus/document/version ID’leri `external_document_mapping` içinde tutulur.
5. Facade isteği Docling `/v1/documents/parse` zincirine çevirir; status/result Docling’den map edilir.
6. Circuit breaker ardışık provider hatalarında açılır.

`OPENCONTRACTS_ENABLED=true` EasyMeeting/local varsayılandır. Base URL
`http://opencontracts:8091`. Token opsiyonel (`OPENCONTRACTS_API_TOKEN`).

Router: OpenContracts açıkken PDF/DOCX buraya gider; kapalıysa Docling kullanılır.
Annotation/corpus sync istendiğinde de OpenContracts seçilir.
