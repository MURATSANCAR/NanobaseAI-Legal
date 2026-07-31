# MinIO Presigned Public Path

Profiles:
- DIRECT_PUBLIC / REVERSE_PROXY when public endpoint is browser-reachable
- BACKEND_PROXY_ONLY (default) — validated fail-safe from v1.0

Never rewrite signed hostnames via string replace. Internal Docker hosts must not leak.

Config: `specai.storage.delivery-mode` / `MINIO_PRESIGN_MODE`.
