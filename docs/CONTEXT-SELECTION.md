# Context Seçimi

`RelevanceClauseContextBuilder` sabit “önceki iki madde” penceresi kullanmaz. Aynı
document version içindeki clause ve tablolar aday olur. Her aday için:

- lexical Jaccard yakınlığı,
- parent/child/sibling yapısal ailesi,
- sayfa yakınlığı

hesaplanır. Ağırlık, minimum relevance ve maksimum öğe sayısı aktif policy'den gelir.
İlgili sayfa aralığıyla kesişen tablolar aday context olarak eklenir.

Seçilen her öğe kaynak tipi, kaynak kimliği, relevance skoru ve sayfa metadatasıyla
model request'e gider; tüm doküman körlemesine LLM'e gönderilmez. İleride embedding,
zeyilname ve uzman örneği sağlayıcıları aynı context portunun arkasına eklenebilir.
