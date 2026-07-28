# Evaluation Pipeline

`evaluation_dataset`, `evaluation_case` ve `evaluation_run` tabloları input/expected
snapshot'ı, difficulty/tags ve baseline-candidate sürümlerini korur.

`GenericEvaluationPolicyEngine`, case metric map'lerini metric adına bağımlı olmadan
ortalama toplar. Quality gate JSON'undaki `minimums` ve `maximums` alanlarını uygular;
tenant'a özgü yeni metrik Java değişikliği gerektirmez.

Önerilen aktivasyon akışı:

1. uzman tarafından öğrenmeye onaylı feedback'i dataset adayına al,
2. baseline ve candidate sürümlerini aynı immutable case'lerde çalıştır,
3. precision/recall, grounding, numeric/unit/category/modality accuracy, latency,
   schema failure ve manual review metric'lerini kaydet,
4. dynamic quality gate'leri değerlendir,
5. yönetici onayı olmadan candidate'ı ACTIVE yapma.

Repository'de müşteri dokümanı içeren production evaluation dataset'i bulunmaz.
Saf evaluation gate davranışı unit testte doğrulanır; gerçek model karşılaştırması için
lokal runtime ve onaylı dataset gereklidir.
