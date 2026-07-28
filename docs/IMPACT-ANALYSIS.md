# Impact Analysis

`GraphImpactAnalysisEngine`, change item’larını dependency graph üzerinde
bounded breadth-first traversal ile izler. `maximumDepth`,
`minimumConfidence` ve `impactConcept` policy’den gelir.

Etkilenen entity; tür, kimlik, impact concept, reason path ve confidence ile
`impact_analysis_result` tablosuna yazılır. Job progress ve sayımlar
`impact_analysis_job`/`impact_analysis_event` içindedir.

Yalnız graph üzerinde erişilen requirement, evaluation ve risk sonuçları
işaretlenir; bütün proje yeniden analiz edilmez. Change set ve impact policy
sürümleri sonucu yeniden üretmek için saklanır.
