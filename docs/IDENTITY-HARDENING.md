# Identity Hardening

Keycloak realm self-registration kapalı, brute-force korumalı, başarısız/login/admin audit açık,
12+ karakter karmaşık password/history policy’li, TOTP destekli, 10 dakika access token,
30 dakika idle session ve refresh token rotation/reuse=0 yapılandırmalıdır. Portal authorization
code + PKCE S256 kullanır; direct grant kapalıdır.

Backend yalnız RS256 kabul eder; issuer, `specai-api` audience, expiry/not-before ve 60 saniye
clock skew doğrular. `tenant_id` yalnız imzalı claim’den UUID olarak alınır; request parametresi
ile override edilemez. Disabled user, revoke ve session enforcement IdP sorumluluğundadır.

Eksik kanıt: gerçek realm import, OTP enrollment, brute-force lockout, refresh reuse, revoke ve
disabled-account testleri.
