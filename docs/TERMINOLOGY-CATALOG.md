# Terminoloji Kataloğu

`terminology_catalog` dil, sektör, doküman türü ve scope ile çözülür.
`terminology_entry.term_type` ile `semantic_role` serbest katalog değerleridir.
Uygulama zorunluluk, yasak, standart, sertifika veya birim kelime listesi içermez.

Clause sinyal üretimi yalnız aktif ve uzman tarafından `APPROVED` edilmiş kayıtları
kullanır. Eşleşen terimlerin ağırlıkları extraction policy'nin `terminology` sinyaline
girdi olur ve yalnız ilgili eşleşmeler prompt'a eklenir.

Model/uzman aday akışı:

1. `POST /api/v1/terminology-catalogs/{id}/candidate-terms`
2. Kayıt `CANDIDATE`, `active=false` oluşturulur.
3. Yönetici `approve` veya `reject` endpoint'ini çağırır.
4. Yalnız onaylanan kayıt aktif olur.

Global katalog tenant tarafından değiştirilemez. Tenant başka tenant'ın kataloğunu RLS
nedeniyle okuyamaz veya aday ekleyemez.
