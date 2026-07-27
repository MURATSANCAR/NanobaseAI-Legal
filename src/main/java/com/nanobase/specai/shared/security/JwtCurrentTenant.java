package com.nanobase.specai.shared.security;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class JwtCurrentTenant implements CurrentTenant {
    @Override
    public TenantPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token) || !authentication.isAuthenticated()) {
            throw new MissingTenantException("Authenticated JWT is required");
        }
        String tenantClaim = token.getToken().getClaimAsString("tenant_id");
        if (tenantClaim == null || tenantClaim.isBlank()) {
            throw new MissingTenantException("JWT tenant_id claim is required");
        }
        try {
            return new TenantPrincipal(UUID.fromString(tenantClaim), token.getName(), extractRoles(token));
        } catch (IllegalArgumentException exception) {
            throw new MissingTenantException("JWT tenant_id claim must be a UUID");
        }
    }

    @SuppressWarnings("unchecked")
    private Set<String> extractRoles(JwtAuthenticationToken token) {
        Set<String> roles = new HashSet<>();
        Map<String, Object> realmAccess = token.getToken().getClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> values) {
            values.forEach(value -> roles.add(String.valueOf(value)));
        }
        return Set.copyOf(roles);
    }
}
