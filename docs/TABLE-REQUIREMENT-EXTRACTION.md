# Table Requirement Extraction

`HeaderContextTableRequirementExtractionStrategy` builds statements as:

```text
Parametre: IP koruma sınıfı; Açıklama: Dış ortam => IP65
```

Isolated short tokens without header context are skipped (`TABLE_HEADER_CONTEXT_MISSING` / `ISOLATED_CELL_SKIPPED`).

Flag: `TABLE_REQUIREMENT_EXTRACTION_ENABLED`. Live E2E-04 **PENDING**.
