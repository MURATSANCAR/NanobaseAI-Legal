# Bilinen Sorunlar ve Doğrulanmamış Alanlar

## Ortam nedeniyle doğrulanamayanlar

- Bu geliştirme makinesinde Docker daemon çalışmadığı için `docker compose up -d`
  ve `docker compose ps` ile tam stack başlatılamadı.
- Aynı nedenle Testcontainers integration suite'i keşfedildi fakat iki test skip
  edildi. PostgreSQL/RabbitMQ/MinIO/Redis canlı entegrasyonu bu makinede geçmiş
  sayılmamalıdır.
- Compose healthcheck ve servisler arası gerçek startup sırası statik
  `docker compose config` doğrulamasından geçti; runtime doğrulaması yoktur.
- PDF/DOCX → MinIO → outbox → RabbitMQ → consumer uçtan uca akışı canlı
  container'larla doğrulanmadı.

## Fonksiyonel sınırlar

- Gerçek OpenContracts/Docling servisi bağlı değildir. Varsayılan disabled adapter
  dokümanı doğru biçimde `MANUAL_REVIEW_REQUIRED` durumuna taşır.
- Sahte clause, page, OCR veya `READY` sonucu üretilmez.
- Gerçek malware tarayıcısı yoktur. `VIRUS_SCANNING` yalnız durum makinesindeki
  aşamadır.
- OCR, encrypted document tespiti, ZIP bomb koruması ve parser sandbox yoktur.
- Integration test sınıfı altyapı, migration, bucket/queue/Redis ve eşzamanlı kod
  üretimini kapsar; prompttaki tüm upload/outbox/DLQ senaryoları henüz container
  seviyesinde otomatikleştirilmemiştir.
- Frontend testleri build sonrasında OIDC/API/status kaynak sözleşmelerini doğrular;
  gerçek browser etkileşimiyle login, wizard, upload, hata, yeni versiyon ve yetkisiz
  route E2E senaryolarını henüz çalıştırmaz.
- MinIO yazımı başarılı olup daha sonraki DB işlemi başarısız olduğunda cleanup
  denenir; cleanup da başarısız olursa orphan object kalabilir.
- PostgreSQL RLS yoktur; izolasyon repository/application sorgularına dayanır.
- Project code sequence globaldir. Kod organization içinde unique'tir fakat her
  organization için ayrı, kesintisiz sayaç değildir.

## Portal sınırları

- Hosted Sites sürümü üretim URL'sinde yayınlandı fakat local varsayılanlarla build
  edilmiştir. Dışarıdan erişilebilir backend/Keycloak URL'leri ve CORS/redirect
  ayarları verilmeden hosted portal gerçek login/API akışına bağlanamaz.
- Portal status güncellemelerinde polling kullanır. SSE endpoint backend'de vardır;
  browser `EventSource` bearer token kısıtı nedeniyle portal buna doğrudan bağlı
  değildir.
- Backend proje ve üye update API'leri tamdır. Portal ayarlar ekranı üye ekleme/çıkarma
  ve archive sunar; üye rol değiştirme ve bütün proje alanlarını update etme UI'ı
  henüz yoktur.
- Login formu portalda parola toplamıyor; güvenlik tasarımı gereği Keycloak hosted
  login ekranına yönlendiriyor.

## Audit sınırı

- Uygulama iş audit'i PostgreSQL `audit_event` tablosundadır.
- Login başarı/başarısızlık event'leri Keycloak'ta etkin ve loglanır; henüz
  uygulamanın append-only `audit_event` tablosuna kopyalanmaz.
