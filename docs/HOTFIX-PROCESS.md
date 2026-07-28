# Hotfix Process

Hotfix yalnız kritik production incident için:

`Incident → Triage → Reproduction → Minimal fix → Regression → Security scan
→ Targeted E2E → Approval → Deployment → Post-validation`

Kapsam genişletilmez. Model, prompt, policy, ontology, terminology veya output schema
değişikliği image değişmese dahi versioned release, evaluation, gate, approval,
rollback ve audit ister.

Hotfix artifact digest/signature ve rollback planı olmadan deploy edilemez.
