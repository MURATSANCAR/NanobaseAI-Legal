# Reproduction Packages

Kritik hatalar `sanitized_input_snapshot` ve `reproduction_package` ile tekrar
üretilebilir hale getirilir.

Paket configuration snapshot ID, expected/actual JSON ve çalıştırma talimatlarını taşır.
Tüm payload recursive sanitizer’dan geçer; secret, token, signed URL, prompt, ham model
giriş/çıkışı ve doküman/evidence metni anahtarları çıkarılır. E-posta ve bearer token
değerleri maskelenir.

Snapshot ve paket SHA-256 content hash ile deduplicate edilir ve UPDATE/DELETE trigger’ı
ile immutable’dır. Paket başka tenant’a RLS nedeniyle görünmez.

API: `POST /api/v1/reproduction-packages`.
