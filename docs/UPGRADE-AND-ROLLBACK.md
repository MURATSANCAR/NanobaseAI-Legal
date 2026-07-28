# Upgrade and Rollback

Sıra: pre-check → encrypted backup → backward-compatible migration → backend → worker/parser/AI
→ frontend → readiness → smoke/UAT → decision. Uzun migration expand/contract; destructive
rollback varsayılmaz.

`upgrade.sh` backup sonrası staged service rollout ve health gate uygular. `rollback.sh` explicit
previous immutable image’lara döner, database’i geri almaz; migration incompatibility için
forward-fix gerekir. Model/prompt/policy/workflow/report snapshot rollback’ı version pointer ile
ve quality/audit gate’iyle yapılır.

Scriptler syntax-checked; gerçek upgrade/rollback koşulmadı.
