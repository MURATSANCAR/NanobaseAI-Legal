# Supply-chain Security

CI gates:

- Java/frontend/Python build and tests; Maven/PNPM locks.
- Semgrep SAST, Gitleaks secret scan, Trivy SCA/secret/IaC/license, Checkov Docker/IaC.
- Her image için Trivy CRITICAL/HIGH fail gate ve CycloneDX SBOM.
- Repository için SPDX SBOM; SARIF/JSON artefact retention.
- Release manifestte image digest, SBOM digest, dependency/license raporu ve scan sonucu.

`latest` architecture script ile yasaktır. Production overlay image tag/digest’i zorunlu yapar.
Release registry’sinde Cosign keyless veya customer key imzası ve deploy-time signature verify
henüz uygulanmadı; go-live blocker’dır. GitHub Actions ref’leri major/version tag kullanıyor;
yüksek güvence ortamında commit SHA pinning yapılmalıdır.

Bu çalışmada yalnız shell architecture scan koştu ve geçti. CI scanner’ları ve container
build’leri yerel Docker olmadığı için çalıştırılmadı.
