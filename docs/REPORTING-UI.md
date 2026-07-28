# Reporting UI

Rapor tasarım tabı tanımları, data policy sürümlerini, template sürümlerini, section
type concept’lerini ve format concept’lerini backend’den yükler.

Akış:

1. Yeni veya mevcut report definition seçilir.
2. Yeni immutable version, sıralı section configuration ile yazılır.
3. Seçili proje üzerinde snapshot preview gösterilir.
4. Geçerli version aktive edilir.
5. Aktif version PDF/DOCX/XLSX format concept’leriyle üretilir.
6. Job ve artifact listesi checksum’lı metadata ile gösterilir; download URL yeni
   sekmede açılır.

UI rapor bölümü veya format enum’u içermez. Şu an section formu temel tek bölüm
oluşturur; gelişmiş condition editor, drag/drop section ordering, branding/theme,
sayfa önizleme ve SSE progress sonraki iterasyondur.
