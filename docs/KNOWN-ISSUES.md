# Known Issues (v1.1)

1. Licensed corpus binaries not checked in — fixtures SKIPPED (`testdata/corpus/assets/*`).
2. Live scanned/DOCX/table/knowledge E2Es not executed — `BROAD_DOCUMENT_GA_READY=false`.
3. MinIO browser presign still needs real public host for DIRECT_PUBLIC profile; proxy-only remains validated fail-safe.
4. Requirement extraction wall-clock remains high on local model path; timing recorder is flag-gated.
5. Host JDK may be unavailable in agent shells — compile/verify via Docker backend build.
6. v1.1 enrichment/delivery/timing/visual gates are wired but default OFF (V33 feature flags).
