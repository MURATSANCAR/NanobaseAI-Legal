# Approval Policy

Onay tanımı `approval_policy` ve immutable `approval_policy_version` ile
versiyonlanır. Request oluşturulduğunda policy configuration snapshot’ı
`approval_request` üzerinde saklanır; daha sonra aktif policy değişse de tarihsel
karar etkilenmez.

`ConfigurableApprovalPolicyEngine` count, percentage, all/any, weighted, sequential,
parallel ve conditional model konfigürasyonlarını değerlendirir. Karar semantiği
`decision_concept_id` ve concept metadata `approvalEffect` alanından gelir; APPROVE
ve REJECT Java enum’una kilitli değildir.

Her karar reviewer, step, yorum, decision snapshot ve timestamp ile
`approval_decision` kaydına eklenir. Aynı reviewer’ın yinelenen kararı ve yetkisiz
step kararı reddedilir. Karar tamamlandığında bekleyen workflow execution devam eder.

Unit test minimum-count ve weighted davranışı kapsar. Delegation modeli tabloda
ayrı bir nesne olarak henüz yoktur; manual override audit içinde tutulur.
