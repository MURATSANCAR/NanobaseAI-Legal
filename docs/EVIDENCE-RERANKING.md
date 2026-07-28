# Evidence Reranking

`EvidenceReranker` bir uygulama port'udur. Varsayılan
`PolicyEvidenceReranker`, candidate sinyallerini aktif retrieval policy
`signals` ağırlıklarıyla birleştirir:

- ontology, lexical ve attribute alignment
- evidence validity ve source authority
- historical expert acceptance
- relation distance
- freshness
- runtime provider sinyalleri

Minimum validity ve top-K sınırı policy'den gelir. Toplam ağırlık sıfırsa skor sıfır
olur; her sinyal 0..1 aralığına alınır. Eşitlikler fragment UUID ile deterministik
çözülür. Sonuç hem seçilen candidate'ları hem rejected count'u ve sinyal etkilerini
döndürür. Böylece sıralama yalnız embedding similarity değildir.

Metrik: `reranking_duration_seconds`.
