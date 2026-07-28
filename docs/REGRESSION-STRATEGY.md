# Regression Stratejisi

Regression suite ve case tenant kapsamlıdır. Case türleri unit, integration, contract,
E2E, evaluation, security ve performance olarak dinamik katalogdan gelir.

Kabul edilmiş hata sınıfları çözülürken `createRegressionCase=true` zorunludur.
Regression input’u sanitize/immutable snapshot, beklenen davranış JSON, severity ve
source feedback referansı taşır.

Release candidate ancak policy’nin zorunlu suite’leri PASS olduğunda ilerleyebilir.
Mevcut 13 Sprint 9 politika/mimari testi kod regression’ını doğrular; müşteri vaka
suite’i henüz oluşturulmadığı için release regression gate’i `NOT_RUN` kalır.
