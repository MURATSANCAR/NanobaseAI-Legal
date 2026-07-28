# Release Gates

20 RC gate definition V15 ile seed edilir ve UI bunları backend’den okur:
build, unit, integration, contract, architecture, frontend, E2E, security,
performance, AI quality, regression, backup, restore, offline install, upgrade,
rollback, UAT, license, documentation ve operations.

Durumlar `PASS`, `FAIL`, `WAIVED`, `NOT_RUN` dinamik concept’leridir.

- PASS en az bir evidence reference ister.
- WAIVED yetkili aktör, gerekçe ve compensating control ister.
- FAIL ve NOT_RUN uygunluğu kapatır.
- Eksik required gate de fail-closed “missing evidence”dır.

Gate sonuçları bu sprintte runtime release’e kaydedilmedi. Test matrisindeki sonuçlar
geliştirme kanıtıdır, release gate result değildir.
