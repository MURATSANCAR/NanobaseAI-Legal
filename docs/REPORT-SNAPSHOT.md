# Report Snapshot

Rapor job’ı başlamadan `report_data_snapshot` oluşturur. Snapshot project, policy
sürümü, captured timestamp, structured payload ve SHA-256 içerir. Artifact aynı
snapshot id’sine bağlıdır.

`report_data_policy_version` hangi entity/alanların dahil edileceğini ve stale
sonuçların BLOCK/WARN/ALLOW etkisini JSON configuration ile belirler.
`ConfigurablePolicyGate` stale kayıtları değerlendirir; uyarı gerekiyorsa snapshot ve
preview içinde görünür, block gerekiyorsa artifact üretilmez.

Render sırasında canlı requirement/risk tablosu yeniden okunmaz. Bu, aynı job’ın
PDF/DOCX/XLSX çıktılarının tutarlı olmasını ve karar support case’in aynı veri
zeminine bağlanmasını sağlar.

Artifact binary, MIME, boyut ve checksum ile kaydedilir. Download endpoint kısa
ömürlü, imzalı uygulama URL’si döndürür. MinIO/object-storage üzerinden gerçek signed
URL E2E testi Docker olmayan bu hostta yapılmadı.
