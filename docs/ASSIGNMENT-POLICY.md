# Assignment Policy

`AssignmentPolicyEngine` bir policy version configuration ve çalışma context’inden
user/group sonucu üretir. `ConfigurableAssignmentPolicyEngine` sinyalleri sıralı
filter/score kurallarıyla değerlendirir:

- Proje üyeliği ve business role.
- Capability/expertise, uygunluk ve workload.
- Önceki reviewer ve conflict-of-interest exclusion.
- Confidentiality, location, SLA ve manual override.

`assignment_policy` aktif sürümü gösterir; configuration yalnız yeni
`assignment_policy_version` ile değişir. Sonuç seçilen aday, group, skor ve gerekçe
taşır. Uygun aday bulunamazsa workflow sessizce yanlış kullanıcıya atanmaz; simulation
finding veya runtime error üretilir.

Mevcut unit test capability, workload ve conflict exclusion sırasını kapsar. Canlı
takvim/HR availability connector’ı yoktur; bunlar yeni sinyal provider’ı olarak
eklenmelidir.
