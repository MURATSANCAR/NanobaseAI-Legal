# Çalıştırma ve Operasyon Rehberi

## Ön koşullar

- Docker Engine ve Docker Compose v2
- Yerel geliştirme/test için isteğe bağlı Java 21 + Maven 3.9
- Frontend'i hostta çalıştırmak için Node.js 22.13+ ve güncel pnpm

## İlk kurulum

```bash
cp .env.example .env
```

`.env` içindeki her `replace-with-...` değerini güçlü ve farklı bir secret ile
değiştirin. `.env` dosyasını commit etmeyin.

## Tüm stack

```bash
docker compose config
docker compose up --build -d
docker compose ps
```

Servisler:

- Portal: `http://localhost:3000`
- API: `http://localhost:8080`
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
```

Readiness PostgreSQL, RabbitMQ, Redis ve MinIO'yu kontrol eder.
`DOCUMENT_INTELLIGENCE_ENABLED=false` iken ilgili bileşen `disabled` olarak sağlıklı
kabul edilir.

## Testler

Backend:

```bash
mvn clean verify
```

Docker daemon erişilebilirse Failsafe `PlatformInfrastructureIT` Testcontainers
testlerini de çalıştırır.

Frontend:

```bash
cd frontend
pnpm install --frozen-lockfile
pnpm run build
pnpm run test
pnpm run lint
```

`npm run build` ve `npm run test` script sözleşmesi açısından da çalışır; repository
kilit dosyası ve CI kurulumu için pnpm tercih edilir.

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
- Queue: `document-processing`
- Retry queues: `document-processing.retry.30s`, `.120s`, `.600s`
- DLQ: `document-processing.dlq`

Outbox backlog için Prometheus'ta `outbox_pending_total`, publish hataları için
`outbox_publish_failed_total` izlenmelidir.

## Sorun giderme

```bash
docker compose logs --tail=200 backend
docker compose logs --tail=200 rabbitmq
docker compose logs --tail=200 minio minio-init
docker compose logs --tail=200 keycloak
```

Kullanıcıdan alınan correlation ID'yi backend loglarında arayın. Token, parola,
dosya içeriği veya presigned URL'yi ticket/log'a kopyalamayın.

Bir outbox kaydı `FAILED` ise `last_error` ve `retry_count` incelenmelidir.
DLQ'daki event, kök neden düzeltilmeden tekrar ana kuyruğa gönderilmemelidir.

## Durdurma ve veri temizleme

Servisleri veriyi koruyarak durdurmak için:

```bash
docker compose down
```

Named volume'ları silen `docker compose down -v` veri kaybına yol açar; yalnız açıkça
istenen disposable geliştirme ortamında kullanın.
