package com.nanobase.specai.document.application;

import com.nanobase.specai.document.domain.ExternalDocumentMapping;
import com.nanobase.specai.document.domain.ExternalDocumentMapping.Provider;
import com.nanobase.specai.document.domain.ExternalDocumentMappingRepository;
import com.nanobase.specai.shared.security.TenantDatabaseContext;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExternalDocumentMappingService {
    private final ExternalDocumentMappingRepository mappings;
    private final TenantDatabaseContext tenantContext;
    private final Clock clock = Clock.systemUTC();

    public ExternalDocumentMappingService(
        ExternalDocumentMappingRepository mappings,
        TenantDatabaseContext tenantContext) {
        this.mappings = mappings;
        this.tenantContext = tenantContext;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ExternalDocumentMapping> find(
        UUID organizationId, UUID versionId, Provider provider) {
        tenantContext.apply(organizationId);
        return mappings.findByDocumentVersionIdAndProviderAndOrganizationId(
            versionId, provider, organizationId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void submitted(UUID organizationId, UUID versionId, Provider provider,
                          String corpusId, String documentId, String providerVersion) {
        tenantContext.apply(organizationId);
        Instant now = clock.instant();
        ExternalDocumentMapping mapping =
            mappings.findByDocumentVersionIdAndProviderAndOrganizationId(
                versionId, provider, organizationId)
                .orElseGet(() -> ExternalDocumentMapping.pending(UUID.randomUUID(),
                    organizationId, versionId, provider, corpusId, now));
        mapping.submitted(documentId, providerVersion, now);
        mappings.save(mapping);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void synced(UUID organizationId, UUID versionId, Provider provider) {
        tenantContext.apply(organizationId);
        mappings.findByDocumentVersionIdAndProviderAndOrganizationId(
            versionId, provider, organizationId)
            .ifPresent(mapping -> mapping.synced(clock.instant()));
    }
}
