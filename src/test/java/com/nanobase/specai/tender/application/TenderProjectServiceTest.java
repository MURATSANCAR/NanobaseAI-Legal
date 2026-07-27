package com.nanobase.specai.tender.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanobase.specai.audit.domain.AuditEventRepository;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.tender.api.TenderContracts.CreateTenderRequest;
import com.nanobase.specai.tender.domain.Priority;
import com.nanobase.specai.tender.domain.TenderProject;
import com.nanobase.specai.tender.domain.TenderProjectRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenderProjectServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

    @Mock
    private TenderProjectRepository projects;
    @Mock
    private AuditEventRepository auditEvents;
    @Mock
    private CurrentTenant currentTenant;

    private TenderProjectService service;

    @BeforeEach
    void setUp() {
        when(currentTenant.require()).thenReturn(new TenantPrincipal(TENANT_ID, "user-42", Set.of()));
        service = new TenderProjectService(projects, auditEvents, currentTenant,
            Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsTenderInsideAuthenticatedTenantAndWritesAuditEvent() {
        CreateTenderRequest request = new CreateTenderRequest(
            "Hastane Bilgi Sistemi", "Örnek Kamu Kurumu", "2026/42",
            LocalDate.of(2026, 12, 1), "TRY", Priority.HIGH, "Pilot ihale");

        var response = service.create(request);

        assertThat(response.code()).startsWith("TND-2026-");
        assertThat(response.name()).isEqualTo("Hastane Bilgi Sistemi");
        verify(projects).save(any(TenderProject.class));
        verify(auditEvents).save(any());
    }

    @Test
    void scopesLookupByAuthenticatedTenant() {
        UUID projectId = UUID.randomUUID();
        when(projects.findByIdAndTenantId(projectId, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(projectId))
            .isInstanceOf(TenderNotFoundException.class);

        verify(projects).findByIdAndTenantId(projectId, TENANT_ID);
    }
}
