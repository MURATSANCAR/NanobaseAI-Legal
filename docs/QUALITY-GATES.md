# Quality Gates

Gate definition/version tenant scope ve approval metadata taşır. `QualityGateEvaluator` tüm
condition’ları fail-closed değerlendirir; eksik metric/config gate’i geçmez. Operator’lar GTE,
GT, LTE, LT’dir.

Minimum condition aileleri: critical requirement recall, grounding, numeric accuracy, evidence
retrieval, conflict false-positive, manual review, latency ve baseline regression. Approved
gate sonucu olmadan model/prompt/policy activation endpoint’i production’da açılmamalıdır.

İki unit test geçti. Aktivasyon servislerine bağlanan integration test ve müşteri threshold’u
yoktur; özellik tam production verified değildir.
