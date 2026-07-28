# Backpressure

`BackpressureService` RabbitMQ `document-processing.request` depth’ini upload kabulünden önce
okur. Configurable delay/reject threshold kararları `ACCEPT`, `ACCEPT_WITH_DELAY`,
`REJECT_TEMPORARILY` üretir; broker görünmüyorsa production fail-closed 503 + `Retry-After`
döner. V14 policy modeli model queue/utilization, job age, quota ve admin override sinyallerini
taşıyabilir.

Eksik: `ACCEPT_WITH_DELAY` için ETA UI, model/GPU utilization adapter, bütün analysis
endpoint’lerinde enforcement ve gerçek queue saturation testi.
