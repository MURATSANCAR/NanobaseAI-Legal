# NANObaseAI SpecAI

On-premise technical specification compliance platform. The repository currently
contains the production foundation for the tenant-isolated tender project API.

## Requirements

- Java 21
- Maven 3.9+
- PostgreSQL 17
- An OpenID Connect provider (Keycloak is the intended deployment target)

## Run locally

```bash
docker compose up -d postgres
export DATABASE_PASSWORD=local-dev-only
export OIDC_ISSUER_URI=http://localhost:8081/realms/specai
mvn spring-boot:run
```

Every API token must include:

- `tenant_id`: an organization UUID
- `scope`: `specai.read`, `specai.write`, or `specai.admin`

The tenant is derived exclusively from the verified JWT. It is never accepted
from a request header or request body.

## Implemented API

```text
POST /api/v1/tenders
GET  /api/v1/tenders
GET  /api/v1/tenders/{id}
PUT  /api/v1/tenders/{id}
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

Document intelligence, requirement extraction, compliance, review, and reporting
will be added as separate modules. Python workers and local models communicate
through explicit contracts; they do not own business decisions.
