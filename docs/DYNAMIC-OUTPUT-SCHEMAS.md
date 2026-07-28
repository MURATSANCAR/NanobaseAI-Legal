# Dinamik Output Schema

`output_schema_definition` görev kimliğini, `output_schema_version` onaylı JSON Schema'yı
tutar. Sector-specific alanlar `requirements[].attributes` altında genişler.

`JacksonOutputSchemaValidator` object/array/scalar type, required, properties, items,
item/string/number sınırları, enum/const, pattern ve additionalProperties sözlüğünü
fail-closed doğrular. Desteklenmeyen schema keyword model çıktısını reddeder.
`x-grounding.unitAliasPaths`, birim alanlarını measurement catalog'a bağlayan kontrollü
metadata'dır.

Model output şema doğrulanmadan `requirement` tablosuna yazılmaz. Şema hatası
`SCHEMA_REJECTED` job event'i ve model run sonucu olarak saklanır; schema repair policy
gelecek retry adapter'ına açık metadata olarak taşınır.
