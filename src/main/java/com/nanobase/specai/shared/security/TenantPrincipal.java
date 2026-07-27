package com.nanobase.specai.shared.security;

import java.util.Set;
import java.util.UUID;

public record TenantPrincipal(UUID tenantId, String subject, Set<String> roles) {
}
