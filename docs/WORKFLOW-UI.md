# Workflow UI

Portal sol menüsündeki “Workflow merkezi” altı backend destekli tab sunar:

- Görevler: dinamik kolonlar, claim ve complete.
- Onaylar: request’e özel decision concept seçenekleri.
- Açıklamalar: candidate, revision, review/approve/send.
- Workflow tasarımı: node ekleme, concept tipi seçme, JSON condition, transition,
  simulation ve activation.
- Raporlar: definition/version, preview, activation, generation/artifact download.
- Yönetici kararı: confidence, factor/provenance ve insan kararı.

Dashboard widget’ları, görev kolonları, node/transition türleri, status/action
seçenekleri, section/format ve executive decision seçenekleri UI içinde enum olarak
tutulmaz. Hepsi UI configuration veya resource response’undan gelir.

Yetkisiz kullanıcıya mutation düğmeleri disabled gösterilir; asıl enforcement
backend’dedir. API hata banner’ı correlation ID’yi gösterir.

Frontend production build ve source-contract testleri çalışır. Drag/drop graph
canvas, browser E2E, keyboard reordering ve signed-in görsel regression bu sprint
kapsamında tamamlanmadı.
