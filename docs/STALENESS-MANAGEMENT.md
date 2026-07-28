# Staleness Management

Impact sonucu compliance evaluation, risk, conflict veya report’a ulaştığında
`analysis_staleness_record` oluşturulur. Status ve trigger ontology concept’tir.
Açık aynı kayıt için partial unique index tekrarları engeller.

Stale kayıt silinmez ve asıl analiz sonucu güncellenmez. Risk listesi açık
staleness concept’ini dinamik kolonda gösterir; detay ekranı uyarı verir.
Re-analysis tamamlandığında yeni sonuç oluşturulmalı ve açık staleness kaydının
`resolved_at` alanı doldurulmalıdır.

Final rapor üreticisi açık stale kayıtları kontrol etmeden sessiz final çıktı
üretemez; bu Sprint 6 güvenlik invariant’ıdır.
