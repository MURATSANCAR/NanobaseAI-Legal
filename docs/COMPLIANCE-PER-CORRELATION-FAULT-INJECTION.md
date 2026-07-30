# Compliance Per-Correlation Fault Injection

## Design

Staging/development only. Hard-disabled when `SPECAI_ENVIRONMENT=production`.

### AI orchestrator

- Env: `FAULT_INJECTION_ENABLED`, `FAULT_INJECTION_TOKEN`
- `POST /v1/test/fault-injection/rules` (token header)
- Actions: `DELAY`, `DELAY_THEN_SUCCESS`, `DELAY_THEN_TIMEOUT` (504 `LLM_GENERATION_TIMEOUT`), `RETURN_503`

### Backend

- Env: `COMPLIANCE_FAULT_INJECTION_ENABLED`, `COMPLIANCE_FAULT_INJECTION_TOKEN`
- `ComplianceFaultInjection` + `/api/v1/internal/compliance-fault-injection/*`
- Pause actions: `PAUSE_AFTER_PREPARE`, `PAUSE_AFTER_MODEL_RESPONSE`, `PAUSE_BEFORE_PERSIST`
- Internal finalize: `POST /api/v1/internal/compliance-jobs/{org}/{job}/finalize`

Rules match by `correlationId` / `jobId` / `taskId`. No document/evidence content in logs.
