# Compliance API

Tüm endpoint'ler JWT tenant context ve project/entity erişim kontrolleriyle çalışır.

## Knowledge

- `POST/GET /api/v1/knowledge/entities`
- `GET/PUT /api/v1/knowledge/entities/{id}`
- `POST /api/v1/knowledge/entities/{id}/merge|split`
- `GET /api/v1/knowledge/entities/{id}/revisions|capabilities`
- `POST /api/v1/knowledge/entities/{id}/attributes|capabilities`
- `PUT/DELETE /api/v1/knowledge/attributes/{id}`
- `POST /api/v1/knowledge/relations`
- `PUT/DELETE /api/v1/knowledge/relations/{id}`
- `PUT /api/v1/knowledge/capabilities/{id}`

## Evidence ve extraction

- `GET /api/v1/evidence[?documentId=&validityStatus=]`
- `GET /api/v1/evidence/{id}|{id}/usages`
- `POST /api/v1/evidence/{id}/verify|invalidate`
- `POST /api/v1/documents/{documentId}/knowledge-extractions`
- `GET /api/v1/knowledge-extractions/{jobId}|{jobId}/events`
- `POST /api/v1/knowledge-extractions/{jobId}/cancel`

## Compliance

- `POST /api/v1/tenders/{projectId}/compliance-analyses`
- `GET /api/v1/compliance-analyses/{jobId}|{jobId}/events`
- `POST /api/v1/compliance-analyses/{jobId}/cancel|retry-failed`
- `GET /api/v1/tenders/{projectId}/compliance-evaluations`
- `GET /api/v1/compliance-evaluations/{id}|{id}/history|{id}/explanation`
- `POST /api/v1/compliance-evaluations/{id}/review|evidence`
- `DELETE /api/v1/compliance-evaluations/{id}/evidence/{linkId}`

Event endpoint'leri önce persisted event geçmişini replay eder, sonra canlı
`SseEmitter` stream'ine bağlanır. Terminal job stream'i tamamlar.
