# Prompt Paketleri

Prompt metni tek Java sabiti değildir. `prompt_component`, `prompt_package` ve
`prompt_package_version` tabloları safety/task/validation/output bileşenlerini
versiyonlar. Paket aktif output schema version'a bağlanır.

Request sırasında assembler:

1. onaylı paket bileşenlerini sırayla yükler,
2. immutable analysis profile metadata'sını ekler,
3. yalnız clause içinde eşleşen onaylı terminolojiyi ekler,
4. policy limitine göre lexical olarak ilgili ontology concept'lerini ekler.

Doküman metni system prompt authority'sine birleştirilmez; orchestrator onu
`untrustedDocumentContext` JSON alanı olarak yollar. Ham system prompt normal kullanıcı
API'sinde gösterilmez. Few-shot sayısı package metadata'sındadır; global baseline sıfır
örnekle gelir ve onaysız örnek production'a alınmaz.
