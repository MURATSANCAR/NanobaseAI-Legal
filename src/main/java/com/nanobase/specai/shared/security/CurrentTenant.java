package com.nanobase.specai.shared.security;

public interface CurrentTenant {
    TenantPrincipal require();
}
