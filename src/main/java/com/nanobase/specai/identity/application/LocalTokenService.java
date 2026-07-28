package com.nanobase.specai.identity.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Service
@ConditionalOnProperty(name = "specai.security.auth-mode", havingValue = "local", matchIfMissing = true)
public class LocalTokenService {
    private final JwtEncoder encoder;
    private final String issuer;
    private final String audience;
    private final long ttlSeconds;
    private final String adminEmail;
    private final String adminPassword;
    private final UUID tenantId;
    private final List<String> adminRoles;

    public LocalTokenService(
        @Value("${specai.security.jwt.secret:}") String secret,
        @Value("${specai.security.jwt.issuer:specai-local}") String issuer,
        @Value("${specai.security.jwt.audience:specai-api}") String audience,
        @Value("${specai.security.jwt.ttl-seconds:28800}") long ttlSeconds,
        @Value("${specai.security.local.admin-email:admin@nanobase.local}") String adminEmail,
        @Value("${specai.security.local.admin-password:}") String adminPassword,
        @Value("${specai.bootstrap.tenant-id:11111111-1111-1111-1111-111111111111}") String tenantId
    ) {
        if (!StringUtils.hasText(secret) || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                "SPECAI_JWT_SECRET must be set and at least 32 bytes for local auth");
        }
        if (!StringUtils.hasText(adminPassword)) {
            throw new IllegalStateException(
                "SPECAI_LOCAL_ADMIN_PASSWORD must be set for local auth");
        }
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.issuer = issuer;
        this.audience = audience;
        this.ttlSeconds = ttlSeconds;
        this.adminEmail = adminEmail.trim().toLowerCase();
        this.adminPassword = adminPassword;
        this.tenantId = UUID.fromString(tenantId);
        this.adminRoles = List.of("SYSTEM_ADMIN", "TENANT_ADMIN", "TENDER_MANAGER");
    }

    public IssuedToken login(String email, String password) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (!adminEmail.equals(normalized) || !adminPassword.equals(password)) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(ttlSeconds);
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(normalized)
            .audience(List.of(audience))
            .issuedAt(now)
            .expiresAt(expires)
            .claim("email", normalized)
            .claim("name", "Local Admin")
            .claim("tenant_id", tenantId.toString())
            .claim("realm_access", Map.of("roles", adminRoles))
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, ttlSeconds, normalized, "Local Admin", adminRoles,
            tenantId.toString());
    }

    public record IssuedToken(
        String accessToken,
        long expiresInSeconds,
        String email,
        String displayName,
        List<String> roles,
        String tenantId
    ) {
    }
}
