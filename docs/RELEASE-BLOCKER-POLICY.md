# Release Blocker Policy

Varsayılan policy versioned `sprint9_policy_version` kaydıdır. Non-deferrable örnekler:

- tenant data leak ve unauthorized access
- data loss ve audit corruption
- backup/restore failure
- critical vulnerability
- rollback unavailable

Triage policy’si tenant izolasyonu, data loss ve audit integrity sinyalini doğrudan
blocker yapar. Açık blocker release package içinde listelenir; approval, GO ve deploy
fail-closed değerlendirmeleri blocker sayısını sıfır ister.

Liste seed’dir; tenant policy sürümüyle genişletilebilir.
