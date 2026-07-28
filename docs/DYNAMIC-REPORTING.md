# Dinamik Raporlama

`report_definition` ve `report_definition_version` raporun kimliği ile immutable
kompozisyonunu ayırır. `report_section_definition` section type concept’i, data
provider configuration, görünürlük condition ve render ayarını taşır.

`ReportSectionDataProvider` extension portudur. `GenericSnapshotSectionDataProvider`
yalnız report snapshot içindeki izinli JSON pointer’ları okur; canlı mutable
repository’den render sırasında veri çekmez. Yeni section provider Java bean’iyle,
yeni section instance’ı ise yalnız configuration ile eklenir.

Template ve data policy ayrı versiyonlanır; generation job kullanılan definition,
template ve policy sürümlerini saklar. Draft → preview → activate akışı UI ve API’de
vardır.

Format renderer registry PDF, DOCX ve XLSX için geçerli minimal dosyalar üretir.
Üretim kalitesinde branding, tablo pagination, font embedding ve accessibility
şablonları tenant template’leriyle genişletilmelidir.
