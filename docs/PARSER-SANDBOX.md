# Parser Sandbox

`document-intelligence` non-root çalışır; read-only root filesystem, 1 GiB sınırlı tmpfs,
`cap_drop: ALL`, `no-new-privileges`, PID/CPU/RAM limiti ve execution timeout kullanır.
PostgreSQL/RabbitMQ/Redis/Keycloak credential’ı almaz. Yalnız private network üzerinden MinIO
source bucket/prefix ve backend callback yüzeyine ihtiyaç duyar; host portu yayınlanmaz.

Object key UUID tenant/document pattern’i ve bucket allowlist ile doğrulanır. Download size,
page count ve timeout ikinci kez parser sınırında kontrol edilir. Job durumu ayrı SQLite
volume’da tutulur; parser hatası backend’i durdurmaz.

Eksik runtime kanıtı: egress firewall, prefix-only MinIO credential, OOM/PID/disk-full ve
restart recovery testleri.
