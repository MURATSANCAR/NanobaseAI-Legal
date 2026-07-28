# Güvenlik Notları

## Uygulanan kontroller

- Backend stateless OAuth2 resource server'dır; JWT issuer ve JWK doğrulaması yapar.
- Organization yalnız doğrulanmış `tenant_id` claim'inden alınır.
- Eksik, bozuk veya UUID olmayan tenant claim fail-closed davranır.
- Global realm rolü ile proje üyeliği/izinleri birlikte değerlendirilir.
- Organization scope repository sorgusuna taşınır; cross-tenant ID değiştirme testleri
  proje okuma, upload, download ve audit için vardır.
- V8 migration tenant tablolarında PostgreSQL RLS ve `FORCE ROW LEVEL SECURITY`
  etkinleştirir. Request transaction'ı tenant değerini yalnız doğrulanmış JWT
  claim'inden `SET LOCAL` eşdeğeriyle alır; repository filtreleri korunmuştur.
- Portal Authorization Code + PKCE S256 kullanır; parola uygulama tarafından alınmaz.
- CORS allowlist environment üzerinden tanımlanır.
- MinIO bucket'ları private'dır ve host portları geliştirmede loopback'e bağlıdır.
- İndirme yalnız kısa süreli presigned URL ile yapılır.
- Upload önce tenant kapsamlı temporary key'e yazılır. Final copy öncesinde boyut ve
  SHA-256 doğrulanır; transaction rollback'inde compensation, sonrasında periyodik
  orphan reconciliation çalışır.
- Dosya adı sanitize edilir; MIME türü Apache Tika ile binary içerikten algılanır.
- Dosya boyutu ve SHA-256 kontrol edilir.
- Exception stack trace, token, binary içerik, parola ve signed URL loglanmaz.
- Audit tablosunda update/delete trigger ile engellenir.
- Doküman görüntüleme, preview, clause görüntüleme, indirme, reprocess ve cancel
  erişimleri audit edilir. Signed URL audit metadata'sına yazılmaz.
- Upload, yeni versiyon, reprocess, signed URL ve SSE istekleri tenant + user + IP
  anahtarına göre Redis Lua rate limit'iyle korunur; Redis erişilemezse instance-local
  fail-safe limit uygulanır.
- Worker event tenant'ını object key tenant prefix'iyle karşılaştırır ve background
  transaction'ında aynı tenant context'ini kurar.
- Docling servisi storage credential'ını yalnız environment'tan alır; bucket/key,
  boyut, sayfa ve timeout sınırlarını uygular; temporary dosyayı temizler ve shell
  komutu çalıştırmaz.
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
- Gerçek malware/ClamAV taraması ve parser sandbox ekleyin. Mevcut
  `VIRUS_SCANNING` durumu bir tarayıcının var olduğu anlamına gelmez.
- Encrypted PDF/DOCX, archive/ZIP bomb ve parser kaynak tüketimi limitleri ekleyin.
- Presigned URL ve object-store access log'ları için merkezi retention politikası
  belirleyin.
- Login endpoint'i Keycloak'tadır; brute-force/login rate limit'i Keycloak ve edge
  proxy üzerinde ayrıca yapılandırın. Backend filter login trafiğini görmez.
- Redis kesintisinde instance-local rate limit replica'lar arasında ortak değildir;
  production edge rate limiter ve Redis HA ile toplam limit garanti edilmelidir.
- Uygulama rolüne `BYPASSRLS` vermeyin. Cross-tenant/system-admin bakım işlemleri
  için login olmayan, ayrı ve denetimli database role sağlayın.
- Keycloak login event'lerini merkezi, değiştirilemez SIEM/audit deposuna aktarın.
- Backup, PITR, MinIO object lock/versioning ve anahtar rotasyonu politikalarını kurun.
- Docling image'ını dependency/image taraması ve SBOM üretimiyle release pipeline'da
  doğrulayın; real-world adversarial PDF/DOCX corpus'u ile fuzz ve resource-exhaustion
  testleri çalıştırın.
