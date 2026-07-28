# Evidence Domain

`evidence_fragment`, Document/DocumentVersion'a bağlı, sayfa, clause, metin,
normalized metin, bounding box, offset, hash, dil, parser/OCR kalitesi ve geçerlilik
aralığı taşıyan tenant-scoped kanıttır. Fragment metni iş loglarına yazılmaz.

`evidence_claim`, fragment içindeki iddiayı subject/predicate/object veya dynamic
`value_json` ile ifade eder. Claim type, predicate ve entity referansları concept/UUID
tabanlıdır. Attribute, relation, capability ve compliance linkleri aynı fragment'i
kullanabilir; `/api/v1/evidence/{id}/usages` bu provenance zincirini döndürür.

Evidence viewer detail endpoint'i belge adı/türü, version, sayfa, kaynak metin,
bounding box ve son validity assessment'i döndürür. Frontend tıklamada signed document
URL + PDF sayfasına gider ve normalize bounding box'ları overlay olarak çizer.
