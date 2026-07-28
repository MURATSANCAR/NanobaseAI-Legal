# Conflict Candidate Generation

`JdbcRiskPersistence.conflictCandidates` önce tenant, proje, concept ve farklı
doküman sürümü filtresi uygular. Böylece bütün clause çiftleri oluşturulmaz.
`StagedConflictCandidateGenerator` kalan küçük kümeyi:

```text
entity scope → concept → structured attribute overlap → version → rerank → limit
```

adımlarıyla puanlar. Minimum retrieval skoru, retrieval/candidate limitleri ve
aktif stage listesi conflict policy’dedir.

Detaylı comparison yalnız limitlenmiş adaylara uygulanır. Deterministik provider
desteklemiyorsa sonuç uydurulmaz; candidate model/manual kuyruğuna bırakılır.
Candidate, rerank ve deterministic/model oranları gözlemlenebilir metriklerdir.
