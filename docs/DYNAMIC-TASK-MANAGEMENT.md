# Dinamik Görev Yönetimi

`WorkManagementService`, workflow TASK action provider’ıdır. Görev türü, öncelik,
durum, dependency ve yorum görünürlüğü ontology concept kimliklerinden çözülür.
Java’da görev türü/statüsü enum’u yoktur.

Model:

- `task_record`: subject, workflow execution, assignment ve lifecycle.
- `task_dependency`: concept ile bağımlılık semantiği.
- `task_comment`: görünürlük concept’i ve auditli yazar.
- `task_attachment`: mevcut document/document_version referansı.
- `task_sla_record` ve `escalation_record`: görevden ayrı policy sonucu.

API’ler liste, detail, claim, complete, block ve comment işlemlerini sunar. Claim
atanmamış görevi oturum kullanıcısına atomik olarak alır. Complete/block işlemi
hedef status concept’ini istemciden alır ve metadata action effect’i ile doğrular.
Tamamlama bekleyen workflow execution’ını tekrar yürütür.

Görev geçmişi fiziksel olarak silinmez. Optimistic version ve audit/outbox yarış ve
izlenebilirlik için kullanılır. Attachment yeni binary katman kurmaz.
