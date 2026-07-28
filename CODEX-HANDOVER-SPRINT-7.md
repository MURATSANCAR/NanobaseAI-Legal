# CODEX Handover — Sprint 7

Tarih: 2026-07-28. Bu belge kodda bulunan ve bu hostta doğrulanabilen durumu
anlatır; açık maddeler ayrıca belirtilmiştir.

![Sprint 7 dinamik workflow görseli](frontend/public/og-sprint7-workflow.png)

## 1. Sprint 4–6 ön koşul doğrulaması

Requirement/revision, compliance/evidence, risk/conflict/ambiguity, clarification ve
mitigation candidate, impact/staleness, feedback, knowledge snapshot,
profile/policy/prompt/model run ve audit yapıları korundu. Ayrıntı
`docs/SPRINT-7-PREREQUISITE-CHECK.md` dosyasındadır.

## 2. Yapılan değişiklikler

V13 ile workflow/work management/report/decision/finalization şeması; application
engine’leri; REST controller/contract’ları; portal workflow merkezi; unit,
architecture ve frontend source-contract testleri eklendi.

## 3. Dynamic workflow yapısı

Definition/version/node/transition ile immutable graph tanımı; instance/token/
execution/log ile runtime ayrıldı. Paralel dallar token üzerinden yürür.

## 4. Workflow node handler sistemi

`WorkflowNodeHandler` registry concept koduyla handler seçer.
`WorkflowNodeActionProvider` task/approval gibi yan etkileri genişletir; yeni concept
mevcut tanımları değiştirmez.

## 5. Condition engine

`SafeJsonWorkflowConditionEngine` all/any/not ve allowlist operator’lü JSON DSL
uygular. Script, SpEL, reflection ve arbitrary class access yoktur.

## 6. Workflow simulation

Simulation erişilemeyen node, dead-end, loop visit limiti, eksik/çakışan transition,
authorization ve terminal yolu finding’lerini kalıcı raporlar. Aktivasyon geçerli
simulation ister.

## 7. Task management

Task, dependency, comment, document attachment, SLA ve escalation kayıtları
tenant-scoped’dur. Claim/complete/block/comment gerçek API ile çalışır.

## 8. Assignment policy

Policy version configuration membership, business role, capability, workload,
availability ve conflict exclusion sinyallerini çözerek user/group seçer.

## 9. Dynamic roles

Workflow rolleri `business_role`/`user_business_role` verisidir. Keycloak rolleri
yalnız platform güvenlik sınırıdır.

## 10. Approval policy

Request policy snapshot’ı saklar. Any/all/count/percentage/weighted/sequential/
parallel/conditional model configuration desteklenir; kararlar concept’tir.

## 11. SLA ve escalation

SLA sürümü ve calendar hedef due/warning/breach üretir. Otomatik periyodik breach
scheduler/dispatcher açık iştir.

## 12. Business calendar

Timezone, çalışma gün/saatleri ve istisnalar configuration’dan okunur. Unit test
kapalı gün atlamasını doğrular.

## 13. Notification sistemi

Template/rule/delivery versiyonludur. In-app çalışır; e-posta gateway adapter
sınırıdır. Sanitizer hassas içerikleri allowlist dışında bırakır.

## 14. Clarification workflow

Candidate gerçek request’e, metin değişiklikleri revision’a, kaynaklar immutable
source’a dönüşür. Review/approve/send/answer action’ları concept status kullanır.

## 15. Clarification answer impact

Answer saklanır ve yeniden analiz outbox event’i oluşur. Canlı broker consumer zinciri
bu hostta doğrulanmadı.

## 16. Dynamic reporting

Definition/version/section, data policy ve template birbirinden ayrıdır.
`ReportSectionDataProvider` yeni bölüm veri kaynaklarının extension portudur.

## 17. Report snapshot

Generation önce checksum’lı structured snapshot alır; tüm formatlar aynı snapshot ve
policy/template/definition sürümlerine bağlanır.

## 18. Report formats

Registry PDF, DOCX ve XLSX byte artifact üretir. Renderer unit testleri MIME/header
ve geçerli container sınırlarını kontrol eder; ileri şablon tasarımı açık iştir.

## 19. Decision support

Versioned policy ağırlıklı faktör, confidence, açıklama ve source reference üretir.
Girdi doğrulanmış report snapshot’tır.

## 20. Executive decision

Öneri ile final karar ayrı alan/kayıtlardır. Final karar yalnız authenticated insan
action’ıyla, dinamik decision concept ve gerekçeyle kaydedilir.

## 21. Finalization

Versioned policy açık iş, stale data, rapor ve insan kararı gate’lerini çalıştırır.
Kullanılan check snapshot ve artifact/policy referansları saklanır.

## 22. Reopen

Reopen eski finalizasyonu silmez; yeni history ve outbox event’i ekler. Otomatik yeni
instance oluşturma henüz bağlı değildir.

## 23. Dynamic dashboard

Definition/version/widget persistence vardır. Sprint 7 widget ve task kolonları
backend UI configuration’dan render edilir.

## 24. Frontend ekranları

Görev, onay, clarification, workflow designer, report designer ve executive decision
tabları eklendi. Mutation butonları yetkiye göre görünür/disabled, backend enforcement
esastır.

## 25. Yeni API’ler

Workflow definition/version/simulation/instance; task; approval; clarification;
report; decision support; finalization; notification ve UI configuration endpoint’leri
eklendi. Tam liste `docs/WORKFLOW-API.md` ve `docs/REPORTING-API.md` içindedir.

## 26. Yeni migration’lar

`V13__dynamic_workflow_task_reporting.sql` Sprint 7 tablolarını, ontology seed’lerini,
indexleri ve FORCE RLS’i ekler. Daha sonra gelen V14 production hardening migration’ı
ayrıca korunur.

## 27. RabbitMQ event’leri

Workflow instance/node/transition, task, approval, clarification, report, decision,
finalize/reopen event’leri mevcut transactional outbox’a yazılır. SLA warning/breach,
notification delivery ve failure event setinin tamamı canlı brokerla testli değildir.

## 28. Çalıştırılan komutlar

```text
JAVA_HOME=<bundled-jdk-21> mvn test
PATH=<bundled-node-and-pnpm> pnpm test
rg / git diff / git status ile statik doğrulama
```

Docker bu hostta bulunmadığından `docker compose`/Testcontainers runtime doğrulaması
yapılmadı.

## 29. Backend test sonuçları

Son koşum: 89 test, 0 failure, 0 error, BUILD SUCCESS; toplam 32.435 saniye.
Detay `docs/TEST-RESULTS-SPRINT-7.md` içindedir.

## 30. Frontend test sonuçları

Vinext production build başarılı. Source-contract suite: 16 test, 16 pass, 0 fail.
Rapor uçları gerçek `/tenders/{id}/reports` sözleşmesine göre doğrulanır.

## 31. Workflow simulation sonuçları

Unit senaryosu entry, parallel split ve iki terminal dalı ziyaret eder; geçerli
sonuç üretir. Unsafe condition ve graph finding davranışı ayrı testlidir. Müşteri
dataset’iyle simulation yapılmadı.

## 32. Performans ölçümleri

Sprint 7’ye özel k6/JMH/soak ölçümü yapılmadı. Unit suite yaklaşık 31 saniye,
frontend production build yaklaşık 3 saniye bandında gözlendi; bunlar performans
SLA kanıtı değildir.

## 33. Güvenlik eksikleri

Canlı RLS/cross-tenant, object-storage signed URL, broker duplicate delivery, report
field masking, approval delegation ve browser authorization E2E testleri eksiktir.

## 34. Tamamlanamayan alanlar

SLA scheduler/escalation dispatcher, dedicated notification retry consumer,
clarification reanalysis consumer E2E, report SSE progress, dashboard/business-role
admin editor, drag/drop designer, reopen-to-new-workflow action ve production-grade
document template özellikleri tamamlanmadı.

## 35. Sonraki sprint önerisi

Önce Docker CI üzerinde V13/RLS/broker/object-store acceptance suite’i; ardından SLA
scheduler ve notification retry; sonra report masking/SSE ve admin designer’lar;
son olarak signed-in Playwright UAT ve kontrollü pilot dataset ile performance/quality
gate önerilir.
