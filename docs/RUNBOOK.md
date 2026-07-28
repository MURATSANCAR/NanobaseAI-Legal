# Çalıştırma ve Operasyon Rehberi

## Ön koşullar

- Docker Engine ve Docker Compose v2
- Yerel geliştirme/test için isteğe bağlı Java 21 + Maven 3.9
- Frontend'i hostta çalıştırmak için Node.js 22.13+ ve npm veya pnpm

## İlk kurulum

```bash
cp .env.example .env
```

`.env` içindeki her `replace-with-...` değerini güçlü ve farklı bir secret ile
değiştirin. `.env` dosyasını commit etmeyin.

## Tüm stack

```bash
docker compose config
docker compose build
docker compose up -d
docker compose ps
docker compose logs --tail=200 backend
docker compose logs --tail=200 document-intelligence
```

Servisler:

- Portal: `http://localhost:3000`
- API: `http://localhost:8080`
- Document intelligence: `http://localhost:8090`
- Keycloak: `http://localhost:8081`
- MinIO console: `http://localhost:9001`
- RabbitMQ management: `http://localhost:15672`

İlk yerel kullanıcı `admin@nanobase.local`, geçici parola `.env` içindeki
`LOCAL_USER_PASSWORD` değeridir. `keycloak-init` bu parolayı realm import'undan sonra
atar ve Keycloak ilk girişte değişim ister.

## Sağlık kontrolü

```bash
curl -fsS http://localhost:8080/actuator/health/liveness
curl -fsS http://localhost:8080/actuator/health/readiness
curl -fsS http://localhost:8080/actuator/prometheus
curl -fsS http://localhost:8090/health/live
curl -fsS http://localhost:8090/health/ready
```

Readiness PostgreSQL, RabbitMQ, Redis ve MinIO'yu kontrol eder.
`DOCUMENT_INTELLIGENCE_ENABLED=false` iken ilgili bileşen `disabled` olarak sağlıklı
kabul edilir. Compose varsayılanında Docling adapter etkin ve
`http://document-intelligence:8090` adresine bağlıdır. OpenContracts opsiyoneldir ve
varsayılan olarak kapalıdır.

## Testler

Backend:

```bash
mvn clean verify
```

Repository Maven wrapper içermez. Docker daemon erişilebilirse Failsafe
`PlatformInfrastructureIT` Testcontainers testlerini de çalıştırır.

Frontend:

```bash
cd frontend
npm ci
npm run lint
npm run test
npm run build
```

Python servis:

```bash
python3.12 -m venv .venv
. .venv/bin/activate
pip install -r services/document-intelligence/requirements.txt
pytest -q services/document-intelligence/test_app.py
```

## MinIO

`minio-init` şu private bucket'ları idempotent oluşturur:

- `specai-original`
- `specai-processed`
- `specai-thumbnails`
- `specai-reports`
- `specai-temp`

Public bucket policy uygulamayın. Production'da bucket provisioning'i IaC ile yönetin.

## RabbitMQ

Beklenen kaynaklar:

- Exchange: `specai.events`
- Request queue: `document-processing.request`
- Result queue: `document-processing.result`
- DLQ: `document-processing.dlq`

Outbox için `outbox_claim_total`, `outbox_reclaimed_total`, `outbox_dead_total` ve
publish hata metrikleri izlenmelidir. `CLAIMED` satırın `claimed_at` süresi claim
timeout'unu aşarsa başka replica geri alır. `DEAD` event otomatik yeniden gönderilmez.

## Docling

Önemli değişkenler:

- `DOCUMENT_INTELLIGENCE_MAX_FILE_BYTES`
- `DOCUMENT_INTELLIGENCE_MAX_PAGES`
- `DOCUMENT_INTELLIGENCE_TIMEOUT_SECONDS`
- `DOCUMENT_INTELLIGENCE_ALLOWED_BUCKETS`
- `DOCLING_TELEMETRY_ENABLED=false`

Job state `document-intelligence-data` volume'ündeki SQLite dosyasında kalır.
Correlation ID tekrarında aynı job döner. Object key mutlaka
`specai-original/{organizationId}/...` prefix'inde olmalıdır.

## Orphan reconciliation

Scheduler `specai-temp` altında grace period'dan eski objeleri listeler. DB'de
referansı olmayanları siler ve başarılı/başarısız sonucu audit/metriklere yazar.
Silme öncesinde organization ve key biçimi tekrar doğrulanır.

## Sorun giderme

```bash
docker compose logs --tail=200 backend
docker compose logs --tail=200 rabbitmq
docker compose logs --tail=200 minio minio-init
docker compose logs --tail=200 keycloak
docker compose logs --tail=200 document-intelligence
```

Kullanıcıdan alınan correlation ID'yi backend loglarında arayın. Token, parola,
dosya içeriği veya presigned URL'yi ticket/log'a kopyalamayın.

Bir outbox kaydı `FAILED` ise `last_error` ve `retry_count` incelenmelidir.
DLQ'daki event, kök neden düzeltilmeden tekrar ana kuyruğa gönderilmemelidir.
Manual replay, tenant/event doğrulaması ve audit olmadan yapılmamalıdır.

## Durdurma ve veri temizleme

Servisleri veriyi koruyarak durdurmak için:

```bash
docker compose down
```

Named volume'ları silen `docker compose down -v` veri kaybına yol açar; yalnız açıkça
istenen disposable geliştirme ortamında kullanın.
