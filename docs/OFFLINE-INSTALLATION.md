# Offline Installation

Connected build ortamı exact image/model/OCR artefact’larını indirip scan/sign eder;
`offline-package.sh` image tar, compose/profile, migration, config, script, docs, license/SBOM ve
SHA-256 manifest paketler. Air-gapped hedefte `install-offline.sh` manifest doğrular, image’ları
load eder ve `--pull never` ile başlatır. Runtime download yasaktır; Docling/OCR/model artifact
path’leri önceden doldurulur.

Preflight CPU/RAM/disk/GPU/Docker/DNS/TLS/dependency/model/clock/backup target kontrol eder.
Upgrade/rollback/health/backup/restore scriptleri dahildir.

Image/model bundle ve SBOM bu çalışmada üretilmedi; offline install koşulmadı. Doküman bir
operasyon sözleşmesidir, acceptance kanıtı değildir.
