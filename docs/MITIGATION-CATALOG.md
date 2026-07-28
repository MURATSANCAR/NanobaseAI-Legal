# Mitigation Catalog

`mitigation_catalog` ve `mitigation_catalog_version` kapsamlı ve onaylı
configuration tutar. `mitigation_pattern`, risk concept + applicable context’i
action concept ve template’e bağlar.

Global bootstrap boş pattern listesiyle gelir; kodda sabit aksiyon metni yoktur.
Eşleşmeler `mitigation_candidate` olarak saklanır. Model üretimi varsa candidate
olarak kalır; `/api/v1/risks/{id}/mitigations` yalnız uzman review durumunu
değiştirir ve otomatik görev açmaz.

Production catalog policy’si expert feedback nedeniyle otomatik değiştirilmez.
Yeni sürüm evaluation quality gate’inden geçip ayrıca onaylanmalıdır.
