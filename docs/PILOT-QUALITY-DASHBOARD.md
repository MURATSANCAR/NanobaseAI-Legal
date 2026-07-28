# Pilot Quality Dashboard

`GET /api/v1/pilot-quality-dashboard` backend UI configuration ve tenant metriklerini
birlikte döndürür. Kartlar ve chart türleri `PILOT_QUALITY_DASHBOARD`
`ui_configuration` kaydından gelir.

Portal şunları gösterir:

- toplam/açık feedback ve blocker
- ortalama çözüm süresi
- regression sayısı
- manual review ve uzman düzeltme oranı
- memnuniyet ve UAT durumu
- kök neden dağılımı, trend ve tekrarlanan hata

Eksik ölçüm “başarılı” veya sıfır kalite problemi olarak yorumlanmaz. Gerçek pilot veri
olmadığından mevcut dashboard boş başlangıç durumunu gösterir.
