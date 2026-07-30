# Compliance Timeout Recovery

Timeouts map to `MODEL_TIMEOUT` / `LLM_GENERATION_TIMEOUT`, never claim failures as
`LLM_UNAVAILABLE`.

Aggregation: successful tasks + permanent timeout → typically `PARTIALLY_COMPLETED`.
