package com.nanobase.specai.audit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanobase.specai.audit.domain.AuditEventRepository;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AuditControllerTest {
    @Mock AuditEventRepository events;
    @Mock CurrentTenant currentTenant;

    @Test
    void auditListIsAlwaysScopedToAuthenticatedOrganization() {
        UUID organizationA = UUID.randomUUID();
        var principal = new TenantPrincipal(
            organizationA, "user-a", Set.of("REPORT_VIEWER"));
        PageRequest pageable = PageRequest.of(0, 25);
        when(currentTenant.require()).thenReturn(principal);
        when(events.findAllByOrganizationId(organizationA, pageable)).thenReturn(Page.empty());

        Page<?> result = new AuditController(events, currentTenant).list(pageable);

        assertThat(result).isEmpty();
        verify(events).findAllByOrganizationId(organizationA, pageable);
    }
}
