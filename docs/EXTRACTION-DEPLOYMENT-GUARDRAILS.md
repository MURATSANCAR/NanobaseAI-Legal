# Extraction Deployment Guardrails

Startup check (when enabled):

```text
documentExtractionWorkerConcurrency + complianceWorkerConcurrency + operationalHeadroom <= databasePoolSize
```

`ExtractionDeploymentGuardrail` — does not modify compliance lease/fencing code.
