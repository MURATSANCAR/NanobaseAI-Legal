# Disk Full

**Belirti:** disk alert, DB/MinIO/parser write failure. **Neden:** object/temp/log/WAL büyümesi.
**Kontrol:** mount bazında usage/inode, lifecycle/backlog. **Müdahale:** admission kapat; yalnız
kanıtlı temp/orphan cleanup; backup/audit/domain object silme. **Geri alma:** cleanup yok;
capacity artır. **Veri riski:** yüksek; checksum ve recovery doğrula. **Eskalasyon:** SRE/storage/
DBA, %95’te P1.
