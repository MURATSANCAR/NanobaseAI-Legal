# Parser Failure

**Belirti:** timeout/failure spike, temp disk veya OOM. **Neden:** crafted/büyük belge, Docling
regression, model eksikliği. **Kontrol:** error code, size/page profile, CPU/RAM/tmpfs; belge
metnini loglama. **Müdahale:** belgeyi manual review’da tut, worker’ı isolate/restart, known-good
image’a dön. Security scan’i bypass etme. **Geri alma:** parser image/config. **Veri riski:**
partial result publish edilmemeli. **Eskalasyon:** document AI + security.
