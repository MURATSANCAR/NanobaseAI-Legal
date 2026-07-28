# Yönetici Karar Desteği

`decision_policy_version` faktörleri, ağırlıkları, threshold ve öneri mapping’ini
configuration olarak taşır. `ConfigurableDecisionSupportPolicyEngine` yalnız
doğrulanmış `report_data_snapshot` verisini kullanır.

Sonuç:

- Önerilen decision concept.
- 0–1 confidence.
- Ağırlık ve etki skorlu faktörler.
- İnsan tarafından okunabilir summary.
- Her faktör için source reference.
- Executive review zorunluluğu.

AI/policy önerisi `decision_support_case.recommended_decision_concept_id` alanındadır;
nihai karar değildir. Yönetici, backend’den gelen dinamik option concept’lerinden
birini seçer ve gerekçeyi `executive_decision` olarak kaydeder. Otomatik executive
decision yazan bir yol yoktur.

Policy engine ve human-authority gate unit testlidir. UI confidence, faktör,
provenance ve öneri/insan kararı ayrımını gösterir.
