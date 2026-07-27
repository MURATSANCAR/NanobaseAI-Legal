package com.nanobase.specai.organization.infrastructure;

import com.nanobase.specai.organization.domain.Organization;
import com.nanobase.specai.organization.domain.OrganizationRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "specai.bootstrap.enabled", havingValue = "true")
public class LocalTenantBootstrap implements ApplicationRunner {
    private final OrganizationRepository organizations;
    private final UUID tenantId;
    private final String tenantName;

    public LocalTenantBootstrap(OrganizationRepository organizations,
                                @Value("${specai.bootstrap.tenant-id}") UUID tenantId,
                                @Value("${specai.bootstrap.tenant-name}") String tenantName) {
        this.organizations = organizations;
        this.tenantId = tenantId;
        this.tenantName = tenantName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!organizations.existsById(tenantId)) {
            organizations.save(new Organization(tenantId, tenantName, Clock.systemUTC().instant()));
        }
    }
}
