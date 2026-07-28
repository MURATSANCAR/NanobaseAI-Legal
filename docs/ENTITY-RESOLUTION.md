# Entity Resolution

`EntityResolutionService` port'u ve `PolicyEntityResolutionService` implementasyonu
normalize isim, identifier, manufacturer, model, version ve geçmiş uzman kabul
sinyallerini policy ağırlıklarıyla birleştirir. Eşikler
`confirmedThreshold`, `possibleThreshold` ve `ambiguityDelta` olarak JSON policy'den
gelir.

Sonuç stringleri workflow çıktısıdır: yeni müşteri sonucu veya eşik davranışı
değiştirmek için policy kullanılabilir. Possible ve ambiguous sonuçlar otomatik merge
edilmez. Birbirine yakın iki aday `ambiguousCandidateIds` ile uzman incelemesine
gider. Normalize işleminde Unicode işaretleri ve noktalama kaldırılır; birleşik/boşluklu
ticari isimler eşdeğer sinyal üretebilir.

Merge/split yalnız explicit kullanıcı API'leriyle ve revision/audit ile yapılır.
