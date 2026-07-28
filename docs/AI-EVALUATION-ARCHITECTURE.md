# AI Evaluation Architecture

V10 dataset/case/run, V14 result-item/comparison fields kullanılır. Her result expected/actual,
metric, error analysis ve model run’a bağlıdır. Katmanlar parser; requirement; knowledge;
compliance; risk/conflict; workflow olarak ayrı metric namespace’lerine sahiptir.

Comparison snapshot model deployment/profile, prompt package, ontology, terminology, extraction/
retrieval/confidence/risk policy’yi dondurur. Quality, latency, resource, schema/grounding failure,
manual review ve expert correction birlikte değerlendirilir. Tek doğruluk metriği aktivasyon
vermez.

Mevcut Sprint 5/6 evaluation script/case’leri sentetik baseline’dır. V14 evaluator gate unit
testleri geçti; gerçek model/golden evaluation run yoktur.
