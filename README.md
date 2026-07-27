# NANObaseAI SpecAI

On-premise technical specification compliance platform. This monorepo contains
the Spring Boot API in the repository root and the React portal in `frontend/`.

## Requirements

- Docker Engine with Compose

## Run locally

Copy secure values into a local `.env`, then:

```bash
docker compose up --build
```

- Portal: `http://localhost:3000`
- API health: `http://localhost:8080/actuator/health`
- Keycloak: `http://localhost:8081`
- MinIO console: `http://localhost:9001`
- RabbitMQ console: `http://localhost:15672`

Every API token must include:

- `tenant_id`: an organization UUID
- a supported realm role such as `TENDER_MANAGER`

The tenant is derived exclusively from the verified JWT. It is never accepted
from a request header or request body.

## Implemented API

```text
POST /api/v1/tenders
GET  /api/v1/tenders
GET  /api/v1/tenders/{id}
PUT  /api/v1/tenders/{id}
POST /api/v1/tenders/{id}/documents
GET  /api/v1/tenders/{id}/documents
GET  /api/v1/documents/{id}/preview
```

Health probes are available at `/actuator/health/liveness` and
`/actuator/health/readiness`.

## Architecture

Packages follow module and layer boundaries:

```text
com.nanobase.specai
├── tender
│   ├── domain
│   ├── application
│   └── api
├── audit
└── shared
```

See [RUNBOOK.md](RUNBOOK.md) for operational instructions and
[CODEX-HANDOVER.md](CODEX-HANDOVER.md) for the delivery report.
