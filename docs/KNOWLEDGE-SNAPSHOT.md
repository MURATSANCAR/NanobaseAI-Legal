# Knowledge Snapshot

Compliance job oluşturulurken `knowledge_snapshot` yazılır. Snapshot tenant/project,
entity ve evidence zaman cutoff'ları, ontology version, terminology snapshot,
kullanılan policy sürümleri ve deterministic content hash taşır.

`ComplianceAnalysisProcessor` aday retrieval'a bu cutoff'ları verir. Fragment veya
entity job başladıktan sonra oluşturulmuşsa değerlendirmeye girmez. Her
`compliance_evaluation`, hem analysis job hem knowledge snapshot FK'sini saklar;
revision ve explanation API'leri bu provenance'ı korur.

Bu yaklaşım fiziksel tablo kopyası çıkarmaz; immutable cutoff + version/policy
referanslarıyla aynı geçmiş görüş yeniden kurulabilir. Soft/end-date değişiklikleri
revision ve timestamp ile izlenir.
