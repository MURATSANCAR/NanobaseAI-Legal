# Requirement UI

Proje detayındaki **Gereksinim matrisi** sekmesi iki endpoint'i paralel yükler:

- requirements page,
- `/api/v1/ui-configurations/requirement-grid`.

UI `columns[].key` yolunu generic `valueAt` ile çözer. Böylece
`attributes.performanceMetric.value` gibi yeni sector alanları frontend release'i
gerektirmez. Temel alias'lar kaynak clause ve concept kimliğini gösterir.

READY ve analize dahil doküman için extraction başlatılabilir. Job progress polling ile
clause/requirement/manual-review sayaçları gösterilir. Satır açıklama çekmecesi source,
profile/version, routing reason, signals, grounding, confidence ve revision bilgilerini
gösterir; ham system prompt göstermez.
