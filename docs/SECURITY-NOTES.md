# Güvenlik Notları

## Uygulanan kontroller

- Backend stateless OAuth2 resource server'dır; JWT issuer ve JWK doğrulaması yapar.
- Organization yalnız doğrulanmış `tenant_id` claim'inden alınır.
- Eksik, bozuk veya UUID olmayan tenant claim fail-closed davranır.
- Global realm rolü ile proje üyeliği/izinleri birlikte değerlendirilir.
- Organization scope repository sorgusuna taşınır; cross-tenant ID değiştirme testleri
  proje okuma, upload, download ve audit için vardır.
- Portal Authorization Code + PKCE S256 kullanır; parola uygulama tarafından alınmaz.
- CORS allowlist environment üzerinden tanımlanır.
- MinIO bucket'ları private'dır ve host portları geliştirmede loopback'e bağlıdır.
- İndirme yalnız kısa süreli presigned URL ile yapılır.
- Dosya adı sanitize edilir; MIME türü Apache Tika ile binary içerikten algılanır.
- Dosya boyutu ve SHA-256 kontrol edilir.
- Exception stack trace, token, binary içerik, parola ve signed URL loglanmaz.
- Audit tablosunda update/delete trigger ile engellenir.
- Compose secret'ları `.env` üzerinden ister; gerçek `.env` Git'e alınmaz.
- Keycloak başarılı/başarısız login event'lerini ve admin event'lerini etkinleştirir.

## Rol modeli

Desteklenen temel roller: `SYSTEM_ADMIN`, `TENANT_ADMIN`, `TENDER_MANAGER`,
`TECHNICAL_REVIEWER`, `REPORT_VIEWER`. Mevcut realm ayrıca gelecekteki
`LEGAL_REVIEWER`, `PROCUREMENT_REVIEWER` ve `EXTERNAL_REVIEWER` rollerini tanımlar;
bu ek roller MVP endpoint politikasında yetkilendirilmiş değildir.

`TENANT_ADMIN` organization projelerinde geniş erişime sahiptir.
`TENDER_MANAGER` proje oluşturur ve üye olduğu/yetkili olduğu projelerde işlem yapar.
Reviewer ve viewer rolleri salt okunurdur.

## Production öncesi zorunlu sertleştirme

- Yerel `LOCAL_USER_PASSWORD` ve bütün `.env` secret'larını secret manager tarafından
  üretilen değerlerle değiştirin; production'da seed kullanıcı oluşturmayın.
- Keycloak redirect URI/web origin değerlerini gerçek HTTPS alan adlarıyla sınırlandırın.
- Backend, Keycloak, MinIO ve broker trafiğini TLS/mTLS ile koruyun; yönetim portlarını
  public ağa açmayın.
- PostgreSQL RLS ekleyerek application scope'a ikinci savunma katmanı kurun.
- Gerçek malware/ClamAV taraması ve parser sandbox ekleyin. Mevcut
  `VIRUS_SCANNING` durumu bir tarayıcının var olduğu anlamına gelmez.
- Encrypted PDF/DOCX, archive/ZIP bomb ve parser kaynak tüketimi limitleri ekleyin.
- Presigned URL ömrü, download audit'i ve object-store access log'ları için merkezi
  retention politikası belirleyin.
- API rate limit, brute-force ve abuse koruması ekleyin.
- Keycloak login event'lerini merkezi, değiştirilemez SIEM/audit deposuna aktarın.
- Backup, PITR, MinIO object lock/versioning ve anahtar rotasyonu politikalarını kurun.
