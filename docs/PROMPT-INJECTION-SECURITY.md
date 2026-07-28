# Prompt Injection Security

Katmanlar: içerik `UNTRUSTED_DOCUMENT` sınıfında tutulur; instruction/tool/exfiltration/schema
sinyalleri regex tabanlı ilk sinyaldir fakat kararın tamamı değildir; bağlam JSON value ve açık
delimiter içine alınır; system authority ile birleştirilmez; tool/network/filesystem yoktur;
strict JSON Schema zorunludur; source grounding ve ontology allowlist uygulanır; sinyal ve
review state `prompt_security_assessment` tablosuna yazılır; yüksek skor insan incelemesi
oluşturur, belgeyi otomatik zararlı ilan etmez.

Invariant: belge sistem prompt’unu, tool yetkisini, tenant scope’u veya çıktı şemasını
değiştiremez. Log yalnız signal code/score/correlation taşır; belge/prompt içeriğini taşımaz.

Kanıt: `services/ai-orchestrator/test_prompt_security.py` izole AI orchestrator ortamında
çalıştırıldı; 2/2 test geçti. AI orchestrator paketinin tamamı 17/17, document intelligence
paketi 4/4 geçti. Bu birim kanıtı gerçek model adversarial/pentest çalışmasının yerine geçmez.
