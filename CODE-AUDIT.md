# Code Audit

## Sonuç

Mimari, modüler monolith yaklaşımını izliyor: `tender`, `document`, `identity`,
`organization`, `audit`, `integration` ve `shared` sınırları ayrılmıştır. Controller'lar
HTTP sözleşmesiyle sınırlı, iş akışları application servislerindedir.

## Bulgular

- OpenContracts bağımlılığı `DocumentIntelligencePort` arkasında izoledir; domain tabloları
  dış sağlayıcı ID'lerini anahtar olarak kullanmaz.
- Tenant ID yalnız doğrulanmış JWT claim'inden alınır. Proje, belge, versiyon ve madde
  sorguları tenant filtresi taşır.
- Binary dosyalar PostgreSQL'e yazılmaz; MinIO object key ve hash saklanır.
- RabbitMQ yalnız dependency değildir: transactional outbox publisher, durable queue ve
  dead-letter queue mevcuttur.
- Audit event'leri uygulama tarafından yazılır, migration trigger'ı update/delete işlemlerini
  engeller.
- Portal artık statik örnek veri kullanmaz; proje, belge, durum, preview ve madde API'lerine
  bağlıdır.

## Kapatılan bulgular

- Eksik frontend kaynakları yeniden üretilebilir portal olarak oluşturuldu.
- Header tabanlı MIME güveni Apache Tika içerik tespitiyle güçlendirildi.
- Document worker, ClamAV, PDF/DOCX parser ve callback akışı eklendi.
- Clause persistence ve madde ağacı API'si eklendi.

## Açık bulgular

- Outbox publisher çoklu instance için `SKIP LOCKED` kullanmıyor.
- Clause parser deterministik MVP parser'dır; karmaşık tablolar ve OCR yapmaz.
- OpenContracts production adapter implementasyonu henüz seçilmiş API sürümüne bağlanmadı.
- Full Compose E2E testi Docker olmayan geliştirme makinesinde çalıştırılamadı.
