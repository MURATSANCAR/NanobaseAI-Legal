# Analysis Profile

Her extraction job için yeni, değiştirilemez `analysis_profile` snapshot'ı oluşturulur.
Snapshot şu sürüm kimliklerini saklar:

- ontology version
- terminology catalog set
- extraction policy
- model routing policy
- confidence policy
- prompt package version
- output schema version

Doküman bağlamı document type/language, clause count, table density, parser status ve
OCR quality değerlerini içerir. Structure complexity etiketi Java eşiğinden değil,
aktif extraction policy'deki `documentProfile.structureComplexityLevels` dizisinden
hesaplanır. Sektör/ihale/iş türleri proje verisinden snapshot'a alınır.

`content_hash`, canonical JSON snapshot'ın SHA-256 özetidir. Profil üzerinde update API
yoktur. Reprocess yeni profil üretir ve önceki job'ı `parent_job_id` ile referanslar.

API:

- `GET /api/v1/analysis-profiles`
- `GET /api/v1/analysis-profiles/{id}`
- `POST /api/v1/analysis-profiles/preview`
