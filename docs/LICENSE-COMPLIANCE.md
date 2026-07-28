# License Compliance

Bu teknik envanter hukuk görüşü değildir. Dağıtılacak exact image/model/tag için release
anında SBOM ve counsel onayı zorunludur.

| Ad | Versiyon | Lisans | Kullanım/dağıtım etkisi | Notice / kaynak riski | Ticari durum |
|---|---|---|---|---|---|
| OpenContracts/cite | Deployment-owned | Current upstream MIT | HTTP adapter; varsayılan kapalı ve bundle edilmez | Exact historical tag ayrıca doğrulanmalı; yanlış AGPL tag yasak | Exact tag onayıyla uygun |
| Docling | 2.43.0 | MIT (code) | Parser image içine bundle | MIT notice; indirdiği model lisansları ayrıdır | Code uygun, model blocker |
| Docling/OCR models | Offline bundle TBD | NOT_VERIFIED | Ağırlıklar müşteriye dağıtılır | Model card/license/acceptable-use notice şart | Onaysız dağıtılamaz |
| PDF.js / pdfjs-dist | 5.6.205 | Apache-2.0 | Frontend viewer | LICENSE/NOTICE ve attribution | Uygun |
| Spring Boot/Apache Tika/MinIO Java SDK | POM’daki exact | Apache-2.0 | Backend jar | Apache LICENSE/NOTICE | Uygun |
| PostgreSQL server | 17.5 | PostgreSQL License | On-prem container | License text | Uygun |
| RabbitMQ server | 4.1.0 | MPL-2.0 | On-prem container | MPL notice; değiştirilmiş dosya source yükümlülüğü | Değişiklik yoksa genelde uygun |
| Redis server | 8.0.2 | Multi-license; exact image terms review | On-prem container | Seçilen lisans ve image source provenance kaydedilmeli | Counsel review |
| MinIO server | RELEASE.2025-04-22 | AGPL-3.0 | On-prem container | Network copyleft/source-offer yükümlülüğü riski | Kritik counsel blocker |
| Keycloak | 26.2.5 | Apache-2.0 | Identity container | Apache notice | Uygun |
| ClamAV | 1.4.3 | GPL-2.0 | Ayrı process/container | GPL source/notice ve dağıtım paketi yükümlülüğü | Counsel review |
| Ollama runtime (örnek) | Deployment-owned | Repo MIT; GUI/binary terms ayrılabilir | Harici lokal runtime | Exact binary ve model lisansı ayrı | Exact artifact review |
| LLM/embedding weights | TBD | NOT_VERIFIED | Offline ağırlık dağıtımı | Ticari kullanım, redistribution, MAU/field-of-use şartları | Go-live blocker |
| Base images | Exact tags | Mixed | Tüm image katmanları | Trivy/Syft license inventory | Release SBOM’a bağlı |

Upstream doğrulama: Docling code MIT; PDF.js Apache-2.0; current OpenContracts/cite MIT;
Ollama repository MIT. Bunlar model ağırlığı veya historical tag lisansını kapsamaz.

AGPL kontrolü: Uygulama Maven/PNPM doğrudan bağımlılık listesinde AGPL paket tespit edilmedi;
ancak MinIO server image açık AGPL değerlendirmesi gerektirir. OpenContracts yalnız explicit
allowlisted deployment tag/digest ve saklanan license hash’i ile etkinleştirilmelidir.
