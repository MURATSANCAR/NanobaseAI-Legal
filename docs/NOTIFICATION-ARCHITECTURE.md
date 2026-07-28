# Notification Mimarisi

Şablon ve routing dinamik tablolardadır: `notification_template`,
`notification_template_version`, `notification_rule`, `notification_delivery`.
Trigger, kanal ve alıcı policy’si concept/JSON configuration ile çözülür.

`NotificationChannelAdapter` provider sınırıdır. Bu sprint:

- In-app adapter: delivery/inbox kaydı oluşturur.
- E-posta adapter: `EmailDeliveryGateway` sınırına güvenli payload verir.

`NotificationPayloadSanitizer` allowlist yaklaşımıyla yalnız proje adı/kodu, görev
metadata’sı, risk seviyesi, son tarih, güvenli uygulama route’u ve correlation ID
taşır. Doküman/evidence tam metni, signed URL, token, prompt, kişisel veri ve model iç
adları drop edilir. Unit test hassas anahtarların çıkarıldığını doğrular.

Delivery status ve provider message id retry/idempotency için saklanır. Teams,
webhook ve SMS adapter’ları eklenebilir fakat bu sprintte çalışan kanal değildir.
SMTP/provider bağlantısı ve broker retry entegrasyonu canlı ortamda doğrulanmadı.
