# Container Hardening

Uygulama Dockerfile’ları fixed base tag, multi-stage where applicable, non-root UID, healthcheck
ve minimum runtime taşır. Compose parser/backend/AI için read-only root, bounded tmpfs,
`cap_drop: ALL`, `no-new-privileges`, PID ve resource limiti uygular. Parser/model host portu
yayınlanmaz. Production overlay yalnız immutable image tag/digest kabul eden zorunlu değişkenler
kullanır.

CI her image için SBOM ve Trivy CRITICAL/HIGH gate üretir. Eksik kanıt: image build/scan/sign
runtime ve base image digest pinning. Tag pinning vardır; registry digest release manifestte
zorunludur.
