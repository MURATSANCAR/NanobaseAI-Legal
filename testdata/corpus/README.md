# Corpus evaluation fixtures (Phase 2/3)

Place anonymized real-world samples here. Do **not** commit secrets or customer-only PDFs.

| ID | Purpose | Expected |
|----|---------|----------|
| E2E-01 | DSİ teknik şartname (external path) | clauses>0, auto req>0, report integrity |
| E2E-02 | Scanned PDF OCR | READY + clauses>0 |
| E2E-03 | DOCX technical spec | clauses>0 |
| E2E-04 | Table-heavy schedule | tables persisted + requirements from cells |
| E2E-05 | Certificate / datasheet | knowledge COMPLETED, purpose=CERTIFICATE |
| E2E-06 | Tenant isolation | cross-tenant 404 on download/proxy |
| E2E-07 | Report integrity negative | tiny stub PDF must FAIL validation |

Harness: `scripts/corpus_e2e_harness.py`
Policy gates: versioned JSON under `testdata/corpus/policy/` (precision/recall thresholds).
