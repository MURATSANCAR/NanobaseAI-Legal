# Experiment Management

`experiment_definition`, `experiment_run` ve immutable `experiment_result` tam
configuration snapshot’ları karşılaştırır. Dataset, metrik ve quality gate seçimi
definition snapshot’ında saklanır.

Provider-dispatched başlangıç türleri offline evaluation, parser benchmark, model,
prompt, policy ve routing karşılaştırmaları, shadow/canary, performance ve security
regression’dır. Türler dinamik katalogdadır.

Run oluşturmak işi `QUEUED` olarak outbox’a yazar; sonuç ayrıca kaydedilene kadar
başarılı gösterilmez. Result; metrik, regression, kaynak tüketimi ve failure özetini
taşır. Quality gate sonucu PASS/FAIL lifecycle’a aktarılır.

Bu sprintte 15 Sprint 5 ve 19 Sprint 6 contract-golden vaka çalışmıştır. Bunlar gerçek
model veya müşteri kabulü değildir ve RC AI gate’ini kapatmaz.
