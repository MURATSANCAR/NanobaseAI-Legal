# Yönetici Kılavuzu

## Tenant ve kimlik

Tenant/user/role işlemlerinde least privilege uygulayın. SYSTEM_ADMIN ve TENANT_ADMIN
release ve configuration yetkisine sahiptir; proje membership ve RLS yine geçerlidir.

## Dinamik kataloglar

Ontology, terminology, policy, workflow, model/prompt ve Sprint 9 concept katalogları
versioned veri olarak yönetilir. Active sürüm yerinde değiştirilmez.

## Pilot ve kalite

Configuration snapshot oluşturun, pilot session başlatın, güvenli telemetry metric
definition’larını kullanın. Feedback type, root cause, severity ve ownership yeni
concept eklenerek genişletilebilir.

## İyileştirme

Candidate için baseline/candidate snapshot, dataset ve gate seçin. Offline PASS,
shadow PASS ve canary PASS sırası atlanamaz. Aktivasyon audit/outbox kaydı üretir.

## Release

RC oluşturulduğunda scope kilitlenir. Digest bazlı manifest, 20 gate, dynamic approval,
staging dry run ve insan GO kararı tamamlanmadan deploy request reddedilir.

## Audit ve diagnostic

Audit geçmişi append-only hash chain’dir. Diagnostic bundle yalnız health, version,
sanitize config, queue, error code, runtime summary ve feature flag taşır; doküman,
evidence, prompt, model I/O veya secret taşımaz.
