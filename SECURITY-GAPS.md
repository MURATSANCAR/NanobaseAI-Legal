# Security Gaps

## Uygulanan kontroller

- OIDC Authorization Code + PKCE
- Realm role tabanlı endpoint yetkisi
- JWT `tenant_id` zorunluluğu
- Tenant filtreli repository sorguları
- Apache Tika içerik tipi tespiti
- ClamAV taraması
- Dosya boyutu sınırı
- Private MinIO bucket ve süreli signed URL
- Append-only audit trigger'ı
- Worker callback için sabit zamanlı shared-secret karşılaştırması
- CORS origin allowlist

## Production öncesi kalanlar

- PostgreSQL Row Level Security
- API ve upload rate limiting
- Worker shared secret yerine mTLS veya workload identity
- ZIP bomb derinlik/ratio kontrolü
- PDF parser sandbox/seccomp profili
- CSP ve reverse-proxy security header'ları
- Keycloak production mode, TLS ve harici PostgreSQL
- Secret manager entegrasyonu
- IDOR, tenant kaçışı ve zararlı corpus security testleri
- ClamAV signature freshness alarmı
