# Sprint 5 Güvenlik Notları

- Tüm Sprint 5 tenant tablolarında `organization_id`, composite tenant FK, repository
  filtresi ve PostgreSQL FORCE RLS bulunur.
- Tenant/global policy/catalog tabloları yalnız `organization_id IS NULL` veya aktif
  tenant görünürlüğüne izin verir.
- Evidence/Document metni loglanmaz. Orchestrator logları correlation ID, profile ve
  hata sınıfıyla sınırlıdır.
- Model internet, tool ve filesystem erişimi olmayan local runtime olarak çağrılır.
  Sistem prompt'u ile untrusted document/evidence JSON'u ayrı mesaj yetkisindedir.
- Orchestrator yalnız request'teki evidence ID'leri kabul eder; positive semantic
  koşul grounded evidence olmadan reddedilir, contradiction gizlenemez.
- Positive final review aktif, geçerli, usable ve grounded evidence olmadan; ya da
  contradiction varken reddedilir.
- GET dahil bütün API'ler JWT role kontrolü altındadır. Mutation'lar manager/reviewer
  rolleriyle sınırlandırılır.
- Signed document URL mevcut kısa ömürlü storage policy'sini kullanır.
- Personel/KVKK attribute masking için UI/config metadata zemini vardır; alan bazlı
  masking enforcement henüz tamamlanmamıştır.
- RLS entegrasyon testi Testcontainers içindedir; Docker olmayan teslim ortamında
  çalıştırılamadı.
