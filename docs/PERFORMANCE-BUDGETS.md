# Performance Budgets

Hedefler `performance_budget` tablosunda tenant/deployment profile’a göre versiyonlanır; aşağıdaki
başlangıç değerleri production onayı değil, staging gate önerisidir.

| Operasyon | Hedef |
|---|---:|
| Proje listesi p95 | 500 ms |
| Doküman metadata p95 | 300 ms |
| Upload başlatma p95 (binary transfer hariç) | 1000 ms |
| Requirement/risk grid p95 | 1000 ms |
| Dashboard p95 | 800 ms |
| SSE event gecikmesi p95 | 2000 ms |
| Rabbit publish p95 | 100 ms |
| Outbox oldest pending | < 30 s |
| Parser throughput, dijital PDF | ≥ 2 page/s/worker |
| Model queue latency p95 | < 30 s |

Staging k6 ve büyük belge sonucu yoktur; bütçelerin hiçbiri doğrulanmış sayılmaz.
