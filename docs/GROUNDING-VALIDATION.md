# Grounding Doğrulaması

Her model adayı en az bir `sourceFragments` öğesi taşımak zorundadır. Pipeline şu
katmanları uygular:

- exact fragment,
- Unicode/whitespace normalize text,
- tüm dinamik attributes içindeki sayıların kaynakta bulunması,
- schema metadata'sındaki unit alias'ların measurement catalog'da çözülmesi,
- parser clause page/bounding-box bağlantısı.

Sonuç `GROUNDED`, `PARTIALLY_GROUNDED` veya `UNGROUNDED` ve coverage/factor evidence
üretir. Ungrounded aday persist edilmez. Partial aday yalnız insan incelemesine gider.
`APPROVED` review state'i domain seviyesinde yalnız `GROUNDED` requirement için
mümkündür.

`requirement_source_fragment` char offset, sayfa aralığı, normalize bounding box ve
grounding method saklar. Requirement code gibi platform alanları policy
`grounding.excludedPaths` ile fact grounding hesabından çıkarılır.
