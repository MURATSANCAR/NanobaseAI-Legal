# Requirement API

## Extraction

- `POST /api/v1/documents/{documentId}/requirement-extractions` — `202`
- `GET /api/v1/requirement-extractions/{jobId}`
- `GET /api/v1/requirement-extractions/{jobId}/events` — SSE
- `POST /api/v1/requirement-extractions/{jobId}/cancel`
- `POST /api/v1/requirement-extractions/{jobId}/reprocess`

Job response bütün version kimliklerini, progress sayaçlarını ve correlation ID'yi taşır.
SSE önce kalıcı event geçmişini replay eder, sonra local live stream'e bağlanır.

## Requirement

- `GET /api/v1/tenders/{projectId}/requirements`
- `GET|PUT /api/v1/requirements/{id}`
- `POST /api/v1/requirements/{id}/review|split|merge`
- `GET /api/v1/requirements/{id}/revisions`
- `GET /api/v1/requirements/{id}/explanation`

API JPA entity döndürmez. `attributes` serbest JSON nesnesidir. Explanation ham prompt
ve internal deployment kimliğini içermez. Update/split/merge source fragment grounding'i
yeniden doğrular ve feedback/revision üretir.
