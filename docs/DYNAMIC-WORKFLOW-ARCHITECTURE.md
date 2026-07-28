# Dinamik Workflow Mimarisi

Sprint 7 çalışma zamanı üç katmandır:

1. Versiyonlu tanım: `workflow_definition`, `workflow_version`, node ve transition.
2. Runtime: instance, paralel token, execution ve immutable transition log.
3. Extension portları: `WorkflowNodeHandler`, `WorkflowConditionEngine` ve
   `WorkflowNodeActionProvider`.

`DynamicWorkflowService` aktif versiyondan instance başlatır. Node tipi Java enum’u
değildir; ontology concept kodu handler registry tarafından çözülür. TASK ve APPROVAL
gibi yan etkiler provider portuna yönlendirilir. Generic otomatik handler, provider
olmayan konfigüre node’lar için güvenli davranış üretir.

Paralellik tokenlarla modellenir. Gateway sonrası her uygun transition yeni bir
tokena dönüşebilir; optimistic `version` kolonları yarışmayı görünür kılar. Her node
çalışması input/output snapshot ile, her geçiş karar context’i ile saklanır. WAIT
node’ları harici task/approval tamamlandığında instance endpoint’inden devam eder.

Tenant bağlamı repository sorguları, `organization_id` ve FORCE RLS ile birlikte
uygulanır. Cross-tenant definition/template kullanımı tenant scope sorgularınca
engellenir. Runtime geçmişi güncellenmez veya silinmez.

Event’ler mevcut outbox üzerinden yayınlanır. Broker idempotency altyapısı platformda
vardır; Sprint 7 event setinin tamamı canlı RabbitMQ ile bu hostta doğrulanmamıştır.
