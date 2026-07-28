# Rate Limiting and Quota

Rate policy resolver endpoint sınıfı, method, tenant, user, IP ve upload content-length sinyalini
kullanır. Upload, signed URL, processing, SSE, search ve admin ayrı config değerleridir. Redis
atomic counter kullanılır; Redis kesintisinde process-local pencere devreye girer. Login/refresh
limiti Keycloak brute-force/client policy sorumluluğundadır.

V14 global quota definition + tenant/project assignment modeli ekler. Project assignment tenant
assignment’ı, o da default’u override eder. Upload öncesi `STORAGE_BYTES` ve `DOCUMENT_COUNT`
enforce edilir; hata quota code/limit/requested usage ile güvenli 422 döner. Diğer seeded quota
kodları analysis/report/SSE/user servislerine henüz bağlanmamıştır.

Gerçek Redis bypass, multi-node consistency ve tenant quota integration testleri koşulmadı.
