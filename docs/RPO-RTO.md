# RPO and RTO

`recovery_policy` tenant/deployment profile bazında `rpo_minutes`, `rto_minutes` ve backup JSON
tutar. Başlangıç önerisi: RPO ≤ 15 dakika, RTO ≤ 120 dakika; müşteri policy’si override eder.

Ölçülen RPO/RTO: **YOK**. Restore testi çalışmadığı için hedef karşılaştırması yapılmadı. RTO,
decrypt başlangıcından sentinel UAT’lerin tamamlanmasına; RPO ise son başarılı transaction/object
timestamp farkına göre hesaplanmalıdır.
