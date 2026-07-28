# Extraction Stratejileri

`PolicyExtractionStrategyResolver`, policy içindeki sıralı `strategies` dizisini okur.
Her stratejinin `when` koşulları runtime feature map üzerinde `equals`, `minimum`,
`maximum` operatörleriyle değerlendirilir.

Bir strateji şu kararları taşır:

- serbest model profile kodu,
- prompt package version,
- table inclusion,
- maximum output tokens,
- validation/retry policy kodları,
- ikinci model doğrulaması,
- output configuration metadata.

Strateji adları Java'da enum değildir. Yeni strateji JSON kaydıyla eklenir. Hiçbir
strateji eşleşmezse extraction sessiz fallback yapmaz; job manual review/failure
görünürlüğüyle fail-closed davranır.
