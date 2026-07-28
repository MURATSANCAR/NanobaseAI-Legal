# SLA ve Escalation

Süreler kodda sabit değildir. `sla_policy_version.configuration_json`, task/project
context ve `BusinessCalendarService` hedef due/warning/breach zamanlarını hesaplar.
Sonuç `task_sla_record` üzerinde kullanılan policy sürümüyle saklanır.

Escalation policy ayrı versiyonlanır. `escalation_record` seviye ve trigger reason
concept’leri, hedef user/group, tetiklenme ve çözülme zamanını tutar. Böylece tenant
farklı öncelik, rol, risk veya proje deadline kuralları tanımlayabilir.

Task oluşturulurken SLA hesabı ve policy snapshot’ı kaydedilir.
`Sprint7SlaScheduler` tenant’ları ayrı transaction/database context ile tarar;
warning ve breach durumlarını idempotent ilerletir, audit/outbox kaydı ve metriği
üretir. Policy’deki `breachEscalation` yapılandırması varsa escalation kaydı açar ve
`task.escalated.v1` event’ini üretir. Calendar unit test hafta sonu/mesai penceresini,
scheduler action testi ise breach’in warning’e önceliğini doğrular.

Sınırlama: çok seviyeli escalation’ın sonraki seviyeye zamanla ilerlemesi ve canlı
zaman/broker davranışı Docker’sız bu ortamda uçtan uca doğrulanmadı.
