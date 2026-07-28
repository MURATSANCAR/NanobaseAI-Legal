# Error Triage

Her feedback tek bir insan onaylı `error_triage_record` üretir. Primary cause dinamik
`ROOT_CAUSE` kataloğundan, secondary cause listesi JSON snapshot’tan gelir.

Öncelik policy’si etkiyi %60, sıklığı %30 ve blocker etkisini %10 ağırlıkla
değerlendirir. Tenant izolasyonu, veri kaybı ve audit bütünlüğü sinyalleri severity’den
bağımsız blocker’dır. Kritik severity de blocker’dır.

Triage kaydı sonradan sessizce değiştirilmez. Yeni değerlendirme için revizyonlu
inceleme akışı gerekir. Kayıt; analizi yapan insanı, zamanı, öneri snapshot’ını,
gerekçeyi ve skorları taşır.
