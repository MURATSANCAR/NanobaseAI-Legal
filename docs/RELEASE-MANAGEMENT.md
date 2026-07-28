# Release Management

Release manifest: SemVer; notes; migration/config/known issue/security fix; SPDX/CycloneDX SBOM;
image digest+signature; model/prompt/policy versions; rollback plan. RC gate: build, unit,
integration, contract, architecture, frontend, E2E, security, load, backup/restore, offline ve
UAT.

Override kaydı kim/neden/risk/expiry/compensating control taşır ve audit edilir. Security,
tenant isolation, restore veya license blocker override ile production onayı alamaz.

Bu çalışma release candidate üretmedi ve image imzalamadı.
