# Architecture Gaps

## Kalan boşluklar

1. `DocumentIntelligencePort` için OpenContracts adapter implementasyonu ve contract testleri.
2. Taranmış PDF için OCR/Docling ve tablo yoğun belgeler için parser routing.
3. Outbox kayıtlarının yatay ölçekli publisher'lar arasında kilitlenmesi ve replay ekranı.
4. SSE tabanlı canlı işlem ilerlemesi; mevcut portal manuel durum yenileme kullanır.
5. Organization ve user lifecycle'ın Keycloak event/webhook ile senkronizasyonu.
6. Clause source coordinate/bounding box alanları.
7. MinIO orphan reconciliation ve lifecycle job'ı.

## Bilinçli sınırlar

- AI/gereksinim çıkarımı bu platform akışına eklenmedi.
- Redis bağlantısı hazırdır ancak henüz cache/lock use-case'i yoktur.
- OpenContracts doğrudan portal tarafından kullanılmaz.
