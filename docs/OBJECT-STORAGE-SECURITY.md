# Object Storage Security

Bucket public değildir; init `anonymous set none` uygular. Object key tenant/project/document/
version prefix’i taşır. Upload quarantine temp prefix’inde başlar; size+SHA-256 doğrulamasından
sonra server-side compose ile final olur. Signed URL yalnız project authorization sonrası,
tek object’e, 5 dakika için verilir ve URL yerine yalnız expiry metadata’sı audit edilir.

Production gerekli fakat runtime kanıtsız: TLS, SSE-KMS/SSE-S3, versioning, lifecycle/temp
cleanup, access log, bucket policy minimum privilege, backup, integrity restore ve object-lock
kararı. Object lock yalnız audit/report retention policy gerektiriyorsa etkinleştirilmelidir.
