# Document Authority Policy

`document_authority_policy` ve sürüm tablosu authority kurallarını tenant/kapsam
bazında saklar. Global bootstrap `rules: []` ve `onUnknown: MANUAL_REVIEW`
değerindedir; zeyilnamenin her zaman üstün olduğu gibi varsayım yoktur.

Conflict model çağrısı seçilmiş iki kaynakla birlikte policy snapshot’ını alır.
Eşleşen authority rule yokken model `preferredSourceId` döndürürse AI
orkestratörü `INVALID_AUTHORITY_ASSUMPTION` ile çıktıyı reddeder. Tercihsiz,
belirsiz ve manual-review gerektiren sonuç geçerlidir.

Signed/draft, official response, version replacement ve sector-specific
öncelikler configuration rule olarak eklenir; Java veya frontend değişmez.
