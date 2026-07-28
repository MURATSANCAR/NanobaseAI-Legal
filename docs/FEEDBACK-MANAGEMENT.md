# Feedback Yönetimi

Feedback merkezi tek yorum alanı değildir. `feedback_case`, `feedback_evidence` ve
`feedback_comment` tenant kapsamlıdır; tip, sınıflandırma, severity, durum ve ekip
dinamik Sprint 9 kataloglarından çözülür.

## Yaşam döngüsü

`OPEN → TRIAGED → IN_PROGRESS → RESOLVED → CLOSED`

`BUG`, `MODEL_ERROR`, `DATA_ERROR`, `CONFIGURATION_ERROR` ve `LABEL_ERROR`
sınıfları regression case olmadan çözülemez. `EXPECTED_BEHAVIOR`,
`FEATURE_REQUEST`, `USABILITY_REQUEST`, `TRAINING_NEED` ve
`CUSTOMER_POLICY_GAP` model hatası olarak kabul edilmez.

## Güvenlik

Evidence yalnız sanitize snapshot ve entity referansı taşır. Metin alanlarında e-posta
ve bearer token maskelemesi, boyut sınırı ve audit history vardır. Tenant RLS her
feedback tablosunda zorunludur.

## API

İstenen create/list/get/triage/assign/resolve/history uçları
`PilotController` içinde uygulanmıştır.
