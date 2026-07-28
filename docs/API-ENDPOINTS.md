# API Endpoint'leri

Base path: `/api/v1`

Tüm iş endpoint'leri `Authorization: Bearer <access-token>` ister. İstemci isteğe bağlı
`X-Correlation-ID` gönderebilir; backend değeri response header'ında geri döndürür.
Liste endpoint'leri Spring Data `page`, `size` ve `sort` parametrelerini kabul eder.

## İhale projeleri

| Yöntem | Endpoint | Açıklama |
|---|---|---|
| `POST` | `/tenders` | Proje oluşturur; owner üyeliğini otomatik ekler. |
| `GET` | `/tenders` | Kullanıcının erişebildiği projeleri sayfalı listeler. |
| `GET` | `/tenders/{id}` | Proje detayını döndürür. |
| `PUT` | `/tenders/{id}` | Projeyi günceller; `version` optimistic lock değeridir. |
| `POST` | `/tenders/{id}/archive` | Projeyi arşivler. |
| `GET` | `/tenders/{id}/members` | Proje üyelerini listeler. |
| `POST` | `/tenders/{id}/members` | Proje üyesi ve proje yetkileri ekler. |
| `PUT` | `/tenders/{id}/members/{memberId}` | Rol ve proje yetkilerini günceller. |
| `DELETE` | `/tenders/{id}/members/{memberId}` | Üyeyi çıkarır; `204` döner. |

Create/update alanları: `name`, `institutionName`, `tenderRegistrationNumber`,
`tenderType`, `businessType`, `sector`, `priority`, `bidDeadline`,
`clarificationDeadline`, `description`, `currency`. Create sırasında `version` yoktur.

Üye alanları: `userId`, `projectRole`, `canViewDocuments`, `canUploadDocuments`,
`canManageMembers`, `canArchiveProject`.

## Dokümanlar

| Yöntem | Endpoint | Açıklama |
|---|---|---|
| `POST` | `/tenders/{projectId}/documents` | `multipart/form-data` dosya yükler. |
| `GET` | `/tenders/{projectId}/documents` | Proje dokümanlarını listeler. |
| `GET` | `/documents/{documentId}` | Doküman ve güncel versiyon detayını döndürür. |
| `GET` | `/documents/{documentId}/versions` | Tüm versiyonları listeler. |
| `POST` | `/documents/{documentId}/versions` | Aynı logical document'a yeni binary versiyon yükler. |
| `POST` | `/documents/{documentId}/reprocess` | Güncel versiyonu yeniden kuyruğa alır. |
| `GET` | `/documents/{documentId}/download-url` | 300 saniyelik presigned URL döndürür. |
| `GET` | `/documents/{documentId}/clauses` | Güncel versiyonun maddelerini döndürür. |
| `GET` | `/documents/{documentId}/processing-events` | `text/event-stream` ilerleme akışı. |

İlk upload multipart alanları:

- `file`: zorunlu PDF veya DOCX.
- `documentType`: zorunlu enum.
- `logicalName`: isteğe bağlı; yoksa sanitize dosya adı kullanılır.
- `includedInAnalysis`: isteğe bağlı, varsayılan `true`.

Yeni versiyon upload'ında yalnız `file` alanı vardır. İzin verilen maksimum boyut
varsayılan 100 MiB'dir.

Doküman türleri:

`TECHNICAL_SPECIFICATION`, `ADMINISTRATIVE_SPECIFICATION`, `DRAFT_CONTRACT`,
`ADDENDUM`, `PRICE_SCHEDULE`, `PRODUCT_CATALOG`, `CERTIFICATE`,
`TECHNICAL_DRAWING`, `OTHER`.

Processing durumları:

`UPLOADED`, `VIRUS_SCANNING`, `CLASSIFYING`, `PARSING`, `OCR_PROCESSING`,
`STRUCTURE_DETECTION`, `INDEXING`, `READY`, `FAILED`,
`MANUAL_REVIEW_REQUIRED`.

## Audit

| Yöntem | Endpoint | Açıklama |
|---|---|---|
| `GET` | `/audit-events` | Yalnız token organization'ına ait audit kayıtlarını sayfalı döndürür. |

## Operasyon endpoint'leri

- `GET /actuator/health`
- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`
- `GET /actuator/prometheus`
- `/v3/api-docs/**` ve `/swagger-ui/**`: yalnız etkinleştirildiğinde ve admin rolüyle.

## Yetki özeti

- `SYSTEM_ADMIN`, `TENANT_ADMIN`, `TENDER_MANAGER`: create/update/upload/reprocess ve
  yönetim işlemleri.
- `TECHNICAL_REVIEWER`, `REPORT_VIEWER`: izinli proje ve dokümanlarda salt okunur.
- Proje kapsamındaki `ProjectMember` izinleri global rol kontrolüne ek olarak uygulanır.
- Kaynak başka organization'a aitse ID bilinse bile veri döndürülmez.

## Hata sözleşmesi

Hatalar `application/problem+json` RFC 7807 biçimindedir:

```json
{
  "type": "https://errors.nanobase.ai/validation-failed",
  "title": "İstek doğrulanamadı",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "Bir veya daha fazla alan geçersiz.",
  "instance": "/api/v1/tenders",
  "correlationId": "00000000-0000-0000-0000-000000000000",
  "fieldErrors": [
    {"field": "name", "message": "boş olmamalıdır"}
  ]
}
```

# Sprint 9 pilot ve release kontrol düzlemi

## Pilot, feedback ve experiments

- `POST /api/v1/configuration-snapshots`
- `GET /api/v1/configuration-snapshots/{id}`
- `POST /api/v1/pilot-sessions`
- `GET /api/v1/pilot-sessions/{id}`
- `POST /api/v1/pilot-sessions/{id}/events`
- `POST /api/v1/pilot-sessions/{id}/metrics`
- `GET /api/v1/pilot-quality-dashboard`
- `GET /api/v1/pilot-concepts/{catalogCode}`
- `POST /api/v1/feedback`
- `GET /api/v1/feedback`
- `GET /api/v1/feedback/{id}`
- `POST /api/v1/feedback/{id}/triage`
- `POST /api/v1/feedback/{id}/assign`
- `POST /api/v1/feedback/{id}/resolve`
- `GET /api/v1/feedback/{id}/history`
- `POST /api/v1/reproduction-packages`
- `POST /api/v1/regression-suites`
- `POST /api/v1/improvement-candidates`
- `GET /api/v1/improvement-candidates`
- `GET /api/v1/improvement-candidates/{id}`
- `POST /api/v1/improvement-candidates/{id}/experiments`
- `POST /api/v1/improvement-candidates/{id}/shadow`
- `POST /api/v1/improvement-candidates/{id}/canary`
- `POST /api/v1/improvement-candidates/{id}/activate`
- `POST /api/v1/improvement-candidates/{id}/reject`
- `GET /api/v1/experiments`
- `GET /api/v1/experiments/{id}`
- `POST /api/v1/experiments/{id}/runs`
- `GET /api/v1/experiment-runs/{id}`
- `GET|POST /api/v1/experiment-runs/{id}/results`
- `POST /api/v1/shadow-executions/{id}/results`
- `POST /api/v1/canary-assignments/{id}/results`
- `POST /api/v1/configuration-activations/rollback`
- `POST /api/v1/quality-debt`
- `POST /api/v1/quality-debt/{id}/accept`
- `POST /api/v1/review-disagreements`
- `POST /api/v1/review-disagreements/{id}/adjudicate`

## Release ve go-live

- `POST /api/v1/releases`
- `GET /api/v1/releases`
- `GET /api/v1/releases/{id}`
- `POST /api/v1/releases/{id}/gates`
- `POST|GET /api/v1/releases/{id}/manifest`
- `POST /api/v1/releases/{id}/artifacts`
- `POST /api/v1/releases/{id}/approve`
- `POST /api/v1/release-approvals/{id}/decisions`
- `POST /api/v1/releases/{id}/dry-run`
- `POST /api/v1/release-dry-runs/{id}/results`
- `POST /api/v1/releases/{id}/go-live-decisions`
- `GET /api/v1/releases/{id}/go-live-package`
- `POST /api/v1/releases/{id}/deploy`
- `POST /api/v1/releases/{id}/deployment-results`
- `POST /api/v1/releases/{id}/rollback`
- `POST /api/v1/releases/{id}/rollback-results`
- `POST|GET /api/v1/releases/{id}/stabilization`
- `GET /api/v1/system/version`
- `POST /api/v1/operations/diagnostic-bundles`

Deploy/rollback request endpoint’leri gerçek sonucu başarılı göstermez; result
endpoint’leri ayrıca runtime evidence ister.
