# SLA ve Escalation

Süreler kodda sabit değildir. `sla_policy_version.configuration_json`, task/project
context ve `BusinessCalendarService` hedef due/warning/breach zamanlarını hesaplar.
Sonuç `task_sla_record` üzerinde kullanılan policy sürümüyle saklanır.

Escalation policy ayrı versiyonlanır. `escalation_record` seviye ve trigger reason
concept’leri, hedef user/group, tetiklenme ve çözülme zamanını tutar. Böylece tenant
farklı öncelik, rol, risk veya proje deadline kuralları tanımlayabilir.

Mevcut uygulama task oluştururken SLA hesabını yapar ve kayıtlar. Calendar unit test
hafta sonu/mesai penceresini doğrular.

Sınırlama: periyodik breach scanner/scheduler ve otomatik çok seviyeli escalation
dispatcher henüz uygulanmadı. `task.sla.warning`, `task.sla.breached` ve
`task.escalated` event’leri bu nedenle canlı zaman ilerletmeli entegrasyon testinden
geçmiş sayılmaz.
