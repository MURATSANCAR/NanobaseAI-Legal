# Document API

| Method | Path | Açıklama |
| --- | --- | --- |
| GET | `/api/v1/documents/{documentId}` | Doküman ve current job özeti |
| GET | `/api/v1/documents/{documentId}/versions` | Version geçmişi |
| GET | `/api/v1/documents/{documentId}/pages` | Sayfalı extracted pages |
| GET | `/api/v1/documents/{documentId}/pages/{pageNumber}` | Tek sayfa |
| GET | `/api/v1/documents/{documentId}/clauses` | Sayfalı/filtreli maddeler |
| GET | `/api/v1/documents/{documentId}/clauses/{clauseId}` | Madde detayı |
| GET | `/api/v1/documents/{documentId}/tables` | Sayfalı tablolar |
| GET | `/api/v1/documents/{documentId}/processing-jobs` | Job geçmişi |
| POST | `/api/v1/documents/{documentId}/reprocess` | Yeni processing job |
| POST | `/api/v1/processing-jobs/{jobId}/cancel` | Job iptali |
| GET | `/api/v1/documents/{documentId}/download-url` | 5 dakikalık signed URL |
| GET | `/api/v1/documents/{documentId}/processing-events` | SSE |
| GET | `/api/v1/processing-jobs/{jobId}/events` | Job SSE |

Clause filtreleri: `parentClauseId`, `clauseType`, `pageNumber`, `search`, `page`,
`size`. Entity’ler doğrudan API response değildir. SSE `Last-Event-ID` kabul eder
ve event ID’leri kalıcı `processing_event` kayıtlarından gelir.

