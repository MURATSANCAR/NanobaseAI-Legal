# Business Calendar

`business_calendar` timezone, çalışma günleri ve mesai pencerelerini JSON
configuration olarak tutar. `calendar_exception` tatil, özel çalışma veya kapanış
gününü concept ile belirtir.

`BusinessCalendarService` tenant timezone’una çevirir, kapalı gün ve saatleri atlar,
sonra çalışma dakikalarını tüketerek due zamanı hesaplar. Sabit “24 saat ekle”
yaklaşımı kullanılmaz. DST geçişleri Java `ZoneId` ile ele alınır.

Policy örneği:

```json
{
  "workingDays": [1, 2, 3, 4, 5],
  "workingHours": [{"start":"09:00","end":"18:00"}],
  "timezone": "Europe/Istanbul"
}
```

Unit test hafta sonundan sonraki ilk çalışma penceresini kapsar. Müşteri tatil
kataloglarının import adapter’ı bu sprintte yoktur.
