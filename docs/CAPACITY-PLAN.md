# Capacity Plan

`capacity_plan` deployment profile, measurement period, workload, resource,
scaling policy ve headroom yüzdesini tenant kapsamında saklar.

Toplanacak pilot sinyalleri: günlük doküman/sayfa/clause, requirement/compliance/risk
sayısı, peak concurrency, average/p95 token ve queue wait.

Tuning yalnız benchmark sonrası yapılır. PostgreSQL index/pool, Rabbit prefetch,
worker concurrency, outbox batch, SSE, Redis, pgvector, MinIO multipart, parser
parallelism, LLM batch/context ve report generation için gerçek pilot ölçümü yoktur.

Bu nedenle resource requirement veya scaling sayısı uydurulmamıştır. Plan durumu
`MEASUREMENT_REQUIRED`; hedef headroom müşteri SLO ve ölçüm sonrası seçilecektir.
