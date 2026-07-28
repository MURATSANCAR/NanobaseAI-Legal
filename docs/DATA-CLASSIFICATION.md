# Data Classification

`data_classification_policy` ve version JSON’u code değişmeden sınıf/enforcement/masking ekler.
Seed başlangıç sınıfları: PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED, PERSONAL_DATA,
SENSITIVE_PERSONAL_DATA, COMMERCIAL_SECRET.

Policy görüntüleme, export, masking, retention, notification, audit ve download kararlarını
resource/scope/role/workflow state’e göre üretmelidir. Classification feature flag değil,
authorization input’udur. Schema/UI policy göstergesi vardır; bütün endpoint enforcement’ı
henüz uygulanmadı.
