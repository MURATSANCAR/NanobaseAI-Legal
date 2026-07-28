# Risk API

## Analiz işleri

- `POST /api/v1/tenders/{projectId}/risk-analyses`
- `GET /api/v1/risk-analyses/{jobId}`
- `GET /api/v1/risk-analyses/{jobId}/events`
- `POST /api/v1/risk-analyses/{jobId}/cancel`
- `POST /api/v1/risk-analyses/{jobId}/retry-failed`

## Risk, belirsizlik ve çelişki

- `GET /api/v1/tenders/{projectId}/risks|ambiguities|conflicts`
- `GET /api/v1/risks|ambiguities|conflicts/{id}`
- `PUT /api/v1/risks/{id}`
- `POST /api/v1/risks/{id}/review|assign|mitigations`
- `GET /api/v1/risks/{id}/history|explanation|propagation`
- `POST /api/v1/ambiguities/{id}/review|interpretations|clarification-candidates`
- `POST /api/v1/conflicts/{id}/review|resolve|clarification-candidates`
- `GET /api/v1/conflicts/{id}/history|explanation`

## Değişiklik ve etki

- `POST /api/v1/documents/{documentId}/change-sets`
- `GET /api/v1/change-sets/{id}` ve `/items`
- `PUT /api/v1/change-sets/{id}/items/{itemId}`
- `POST /api/v1/change-sets/{id}/impact-analyses`
- `GET /api/v1/impact-analyses/{id}` ve `/events`

GET erişimleri project membership ve tenant scope kontrol eder. Mutasyonlar
technical reviewer veya yönetici rollerine açıktır. Hata yanıtları correlation
ID taşıyan platform problem formatını kullanır.
