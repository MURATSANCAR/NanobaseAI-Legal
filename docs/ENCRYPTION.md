# Encryption

Transit: production guard PostgreSQL `sslmode=verify-full` ve HTTP bağımlılıklarında HTTPS
olmadan backend’i başlatmaz; RabbitMQ/Redis TLS, browser/gateway TLS ve private service network
production profile şartıdır.

At rest: PostgreSQL/MinIO volume encryption deployment platformunda; MinIO SSE; backup ve
export’lar `age`; secret store platform encryption; model ağırlıkları ve offline bundle hash/
imza kontrolü uygulanır. Anahtarlar repository/image/log/API’ye girmez.

Development insecure endpoint kullanabilir. Production guard, bootstrap veya ClamAV disable
konfigürasyonunu reddeder. Gerçek certificate-chain ve storage encryption testi koşulmadı.
