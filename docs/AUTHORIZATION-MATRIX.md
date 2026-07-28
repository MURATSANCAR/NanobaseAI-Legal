# Authorization Matrix

Her satırda tenant eşleşmesi zorunludur. Project resource’larında üyelik/role kontrolü,
workflow resource’larında assignment/state kontrolü ikinci katmandır. Feature flag hiçbir
zaman bu kontrolleri gevşetmez.

| Resource | Read | Create/Update | Approve/Override | Ek koşul |
|---|---|---|---|---|
| Project | Üye, tenant admin, system admin | Tender manager/admin | Tenant/system admin | Project membership |
| Document/Version/Clause | Proje üyesi ve viewer+ | Manager/reviewer upload | Security review: admin | Sensitivity/download policy |
| Requirement | Proje reviewer+ | Technical reviewer/manager | Assigned reviewer/admin | Workflow state |
| KnowledgeEntity/Evidence | Proje reviewer+ | Technical reviewer/manager | Evidence verifier/admin | Classification/export policy |
| ComplianceEvaluation | Proje reviewer+ | Technical reviewer/manager | Assigned reviewer/admin | Evidence links tenant scoped |
| Risk/Conflict/Clarification | Proje reviewer+ | Reviewer/manager | Risk owner/admin | Assignment/workflow |
| Task | Assignee/project manager/admin | Assignee/manager | Manager/admin override | Dependency/state/SLA |
| Approval | Assigned approver/admin | Assigned approver | Tenant/system admin | Approval step/state |
| Report | Report viewer + project member | Manager/assigned workflow | Executive/admin | Immutable snapshot, sensitivity |
| DecisionSupportCase | Executive/manager/admin | Executive/manager | Authorized executive | Finalization policy |
| AuditEvent | Tenant/system admin | Uygulama/system event only | Yok | Append-only; sensitive payload yok |
| Configuration | Tenant/system admin | Tenant/system admin | Four-eyes approver | Version/quality gate |
| Operations/AI Quality | Tenant/system admin | Ayrı admin API | System admin | İç model/prompt normal kullanıcıya yok |

Endpoint test kapsamı: controller-level role kontrolleri kısmi; ProjectAccess ve tenant repository
testleri vardır. Bütün endpoint kombinasyonlarını kapsayan JWT/üyelik matrisi henüz koşulmadı ve
go-live blocker’dır.
