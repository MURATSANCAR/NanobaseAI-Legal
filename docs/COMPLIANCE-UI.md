# Compliance UI

Frontend tek portal içinde backend-backed Knowledge Center ve Compliance Workspace
sekmelerini sunar.

Knowledge Center:

- Entity türü ve sekmeler `/api/v1/ui-configurations/entity-types/{conceptId}` üzerinden.
- Dynamic attribute renderer `valueRenderers` konfigürasyonundan.
- Capabilities, relations, evidence ve revisions aynı detail görünümünde.
- Evidence seçimi PDF sayfasını ve bounding box overlay'ini açar.

Compliance Workspace:

- Sol panel requirement/koşul ve kaynak bağlamı.
- Orta panel supporting/contradicting evidence + PDF.
- Sağ panel suggested/final decision, comparison, confidence faktörleri, warning ve
  review aksiyonları.
- Matrix kolonları `COMPLIANCE_MATRIX` UI configuration'dan gelir.
- Decision seçenekleri `/api/v1/ui-configurations/compliance-decisions` ile aktif
  ontology concept'lerinden yüklenir.

Frontend testleri entity formu, matrix kolonları, contradiction görünürlüğü ve gerçek
API yollarının hardcoded demo verisi kullanmadığını kontrol eder.
