# UAT Plan

Her test için gerçek sonuç/kanıt/test eden/tarih çalışma sırasında doldurulur. Başlangıç durumu
`NOT_RUN` olup boş alan başarı sayılmaz.

| ID | Ön koşul | Adımlar | Beklenen sonuç | Gerçek/Kanıt/Test eden/Tarih | Durum |
|---|---|---|---|---|---|
| UAT-01 | Manager tenant user | Proje oluştur | Tenant scoped proje ve audit | Bekliyor | NOT_RUN |
| UAT-02 | UAT-01, ClamAV up | Teknik şartname yükle | Quarantine→SAFE→processing | Bekliyor | NOT_RUN |
| UAT-03 | Gerçek Docling | Clause viewer aç | Doğru sınır/hiyerarşi/source | Bekliyor | NOT_RUN |
| UAT-04 | Lokal model | Extraction başlat | Grounded requirement’lar | Bekliyor | NOT_RUN |
| UAT-05 | Reviewer assignment | Düzelt/onayla | Revision/audit saklanır | Bekliyor | NOT_RUN |
| UAT-06 | Firma/ürün belgeleri | Belgeleri yükle | Güvenli knowledge ingestion | Bekliyor | NOT_RUN |
| UAT-07 | Knowledge complete | Evidence bağla | Source navigasyonu/validity | Bekliyor | NOT_RUN |
| UAT-08 | Evidence mevcut | Compliance çalıştır | Açıklanabilir öneri | Bekliyor | NOT_RUN |
| UAT-09 | Analiz complete | Risk/conflict aç | Grounded finding ve severity | Bekliyor | NOT_RUN |
| UAT-10 | Reviewer | Clarification oluştur | Source-linked soru/revision | Bekliyor | NOT_RUN |
| UAT-11 | Workflow active | Task/approval tamamla | Doğru transition/SLA | Bekliyor | NOT_RUN |
| UAT-12 | Approved snapshot | Rapor üret | Immutable artifact/download | Bekliyor | NOT_RUN |
| UAT-13 | Executive | Karar kaydet | Policy factors + audit | Bekliyor | NOT_RUN |
| UAT-14 | Base analysis | Zeyilname/impact çalıştır | Staleness/impact görünür | Bekliyor | NOT_RUN |
| UAT-15 | Tüm blocker kapalı | Finalize et | Guard’lar geçer, snapshot kilitlenir | Bekliyor | NOT_RUN |
