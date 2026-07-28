# Risk UI

Proje içindeki “Risk merkezi”:

- kolonları `GET /api/v1/ui-configurations/risk-grid` üzerinden yükler,
- risk analysis job progress ve sayımlarını poll eder,
- probability/impact/exposure/confidence değerlerini gösterir,
- staleness uyarısını görünür kılar,
- source, factor, propagation, mitigation ve revision detaylarını açar,
- kaynak butonuyla signed PDF URL’sinin ilgili sayfasına gider,
- uzman onay/red aksiyonlarını gerçek review API’sine yazar.

Risk concept/severity seçenekleri frontend sabiti değildir. Grid’e yeni kolon
eklemek UI deployment gerektirmez; yeni field API response’unda mevcutsa
configuration ile görünür yapılır.
