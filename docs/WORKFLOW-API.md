# Workflow ve Work Management API

Tüm uçlar Bearer JWT, correlation ID, tenant context ve permission kontrolü ister.
ProblemDetail hata sözleşmesi kullanılır.

## Definition ve runtime

```text
GET  /api/v1/workflows
POST /api/v1/workflows
POST /api/v1/workflows/{definitionId}/versions
POST /api/v1/workflow-versions/{versionId}/simulate
POST /api/v1/workflow-versions/{versionId}/activate
POST /api/v1/workflow-instances
GET  /api/v1/workflow-instances/{instanceId}
POST /api/v1/workflow-instances/{instanceId}/resume
POST /api/v1/workflow-instances/{instanceId}/cancel
```

## Tasks ve approval

```text
GET  /api/v1/tasks?projectId=
GET  /api/v1/tasks/{taskId}
POST /api/v1/tasks/{taskId}/claim
POST /api/v1/tasks/{taskId}/complete
POST /api/v1/tasks/{taskId}/block
POST /api/v1/tasks/{taskId}/comments
GET  /api/v1/approvals?projectId=
GET  /api/v1/approvals/{approvalId}
POST /api/v1/approvals/{approvalId}/decisions
```

## Clarification, decision ve finalization

```text
GET  /api/v1/tenders/{projectId}/clarifications
POST /api/v1/clarifications/{id}/revisions
POST /api/v1/clarifications/{id}/submit-review
POST /api/v1/clarifications/{id}/approve
POST /api/v1/clarifications/{id}/mark-sent
POST /api/v1/clarifications/{id}/answers
GET  /api/v1/tenders/{projectId}/decision-support-cases
POST /api/v1/tenders/{projectId}/decision-support-cases
POST /api/v1/decision-support-cases/{id}/decisions
POST /api/v1/tenders/{projectId}/finalize
POST /api/v1/tenders/{projectId}/reopen
GET  /api/v1/tenders/{projectId}/finalization-history
```

UI configuration uçları workflow concept, task-grid, report-designer,
decision-policies ve dashboard verisini dinamik döndürür.
