# Reporting API

```text
GET  /api/v1/report-definitions
POST /api/v1/report-definitions
POST /api/v1/report-definitions/{definitionId}/versions
POST /api/v1/report-definition-versions/{versionId}/preview
POST /api/v1/report-definition-versions/{versionId}/activate
GET  /api/v1/tenders/{projectId}/reports
POST /api/v1/tenders/{projectId}/reports
GET  /api/v1/reports/{jobId}
GET  /api/v1/reports/{jobId}/artifacts
GET  /api/v1/report-artifacts/{artifactId}/download-url
GET  /api/v1/ui-configurations/report-designer
```

Generation isteği aktif `reportDefinitionVersionId` ve backend’den alınan
`formatConceptIds` gönderir. Job response snapshot id, status concept, progress,
error ve artifact metadata’sı taşır.

Preview artifact yazmadan aynı snapshot/data-policy/section çözümünü döndürür.
Download endpoint binary’yi doğrudan sızdırmak yerine erişim kontrollü, kısa ömürlü
URL sözleşmesi verir.

Job mevcut implementasyonda request içinde tamamlanır ve progress kalıcıdır. SSE job
progress stream’i henüz yoktur.
