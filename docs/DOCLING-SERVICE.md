# Docling Document Service

Servis: `services/document-intelligence`.

Endpoint’ler:

- `GET /health/live`
- `GET /health/ready`
- `POST /v1/documents/parse`
- `GET /v1/jobs/{jobId}`
- `GET /v1/jobs/{jobId}/result`
- `POST /v1/jobs/{jobId}/cancel`

Job durumu SQLite volume’ünde kalıcıdır; yalnız process memory’sine bağlı değildir.
Correlation ID unique olduğu için aynı submit tekrarında aynı job döner.

Güvenlik:

- S3 credential yalnız environment’tadır.
- Bucket allowlist ve `specai-original/{organizationId}/...` key biçimi doğrulanır.
- Dosya boyutu, sayfa sayısı ve işlem timeout’u konfigüre edilir.
- Temporary dosya `finally` bloğunda silinir.
- Shell/subprocess komutu ve dosya adı interpolasyonu kullanılmaz.
- CPU/memory limitleri Compose’ta belirtilir.
- Telemetri `DOCLING_TELEMETRY_ENABLED=false` ile kapatılabilir.

Servis gerçek `DocumentConverter` kullanır; sentetik metin/madde üretmez. Heading
ve provenance bulunmuyorsa clause listesi boş kalabilir.

