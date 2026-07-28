# Network Flow Matrix

| Kaynak | Hedef | Port | Protokol | Production gerekliliği |
|---|---|---:|---|---|
| Browser | Frontend/Gateway | 443 | HTTPS | Zorunlu, public edge |
| Frontend | Backend | 443 | HTTPS | Gateway üzerinden |
| Browser | Keycloak | 443 | OIDC/HTTPS | PKCE; doğrudan internal admin yok |
| Backend | PostgreSQL | 5432 | TLS | `verify-full`, private data network |
| Backend | MinIO | 9000 | HTTPS | Private, tenant-prefix policy |
| Backend | RabbitMQ | 5671 | AMQPS | Private |
| Backend | Redis | 6380/6379 | TLS | Private |
| Backend | Keycloak | 8443 | HTTPS | Issuer/JWK private route |
| Backend | ClamAV | 3310 | TCP/private | Upload stream only |
| Backend | Document intelligence | 8090 | HTTPS/private | Parser API only |
| Backend | AI orchestrator | 8090 | HTTPS/private | Logical model API |
| Document intelligence | MinIO | 9000 | HTTPS | Yalnız source prefix/read |
| AI orchestrator | Model runtime | configurable | HTTPS/private | Kullanıcı ağına kapalı |
| Services | OTel collector | 4317/4318 | OTLP/TLS | Metadata only |
| Prometheus | Service metrics | 8080/8090 | HTTPS/private | Monitoring identity |

`compose.yaml` geliştirmede host erişimini loopback ile sınırlar; parser ve AI orchestrator host
portları kaldırılmıştır. Production overlay tüm data-plane portlarını kapatır. Firewall/network
policy runtime doğrulaması yapılmamıştır.
