# Known Issues

## Blocking before production

- Portal Keycloak PKCE ve backend verisine bağlıdır.
- Tika ve ClamAV kontrolleri vardır; archive bomb ve parser sandbox kontrolleri yoktur.
- OpenContracts adapter uygulanmadı; built-in worker dijital PDF/DOCX işler.
- OCR ve karmaşık tablo çıkarımı uygulanmadı.
- PostgreSQL RLS etkin değil; izolasyon uygulama repository katmanında uygulanıyor.

## Operational

- Outbox retry sayısı 10 ile sınırlı fakat başarısız kayıtlar için yönetim endpoint'i yok.
- Rabbit publisher confirm sonucu ayrıca kalıcı olarak takip edilmiyor.
- Orphan MinIO object reconciliation job'ı yok.
- MinIO bucket lifecycle politikaları tanımlı değil.
- Backup/restore otomasyonu yok.

## Development environment

- Yerel Keycloak kullanıcısının ilk parolası geçicidir.
- Compose içindeki varsayılan secret'lar production için uygun değildir.
- Docker bulunmayan geliştirme makinelerinde tam entegrasyon testi çalışmaz.
