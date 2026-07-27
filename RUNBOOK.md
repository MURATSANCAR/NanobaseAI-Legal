# Local Runbook

## Başlatma

Production olmayan yerel ortam için `.env` oluşturun:

```dotenv
DATABASE_PASSWORD=local-dev-only
MINIO_ACCESS_KEY=specai
MINIO_SECRET_KEY=local-minio-secret
RABBITMQ_USER=specai
RABBITMQ_PASSWORD=local-rabbit-secret
REDIS_PASSWORD=local-redis-secret
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=local-keycloak-admin
```

Ardından:

```bash
docker compose up --build
```

## Kontrol

```bash
curl http://localhost:8080/actuator/health
docker compose ps
docker compose logs --tail=100 backend
```

Servis adresleri:

- Portal: `http://localhost:3000`
- API: `http://localhost:8080`
- Keycloak: `http://localhost:8081`
- MinIO: `http://localhost:9001`
- RabbitMQ: `http://localhost:15672`

Yerel Keycloak kullanıcısı `admin@nanobase.local`, geçici parolası
`change-on-first-login` değeridir. İlk girişte değiştirilmelidir.

## Build ve test

```bash
mvn verify
cd frontend
npm ci
npm run build
```

## Dosya yükleme sözleşmesi

`POST /api/v1/tenders/{projectId}/documents` isteği multipart olmalıdır:

- `type`: `TECHNICAL_SPECIFICATION` gibi bir `DocumentType`
- `file`: PDF veya DOCX binary

Authorization header içinde Bearer token zorunludur.

## Durdurma

```bash
docker compose down
```

Veriyi de silmek bilinçli ve geri alınamaz bir yerel işlem olduğundan yalnız gerektiğinde
`docker compose down --volumes` kullanın.
