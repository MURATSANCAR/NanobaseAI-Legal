package com.nanobase.specai.tender.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.tender.domain.ProjectMemberRepository;
import com.nanobase.specai.tender.domain.TenderProjectRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectAccessServiceTest {
    @Mock TenderProjectRepository projects;
    @Mock ProjectMemberRepository members;

    @Test
    void cannotReadProjectFromAnotherOrganization() {
        UUID organizationA = UUID.randomUUID();
        UUID projectB = UUID.randomUUID();
        TenantPrincipal userA = new TenantPrincipal(
            organizationA, "user-a", Set.of("TENANT_ADMIN"));
        when(projects.findByIdAndOrganizationId(projectB, organizationA))
            .thenReturn(Optional.empty());

        ProjectAccessService service = new ProjectAccessService(projects, members);

        assertThatThrownBy(() -> service.requireView(projectB, userA))
            .isInstanceOf(TenderNotFoundException.class);
        verify(projects).findByIdAndOrganizationId(projectB, organizationA);
    }

    @Test
    void cannotUploadToProjectFromAnotherOrganization() {
        UUID organizationA = UUID.randomUUID();
        UUID projectB = UUID.randomUUID();
        TenantPrincipal userA = new TenantPrincipal(
            organizationA, "user-a", Set.of("TENDER_MANAGER"));
        when(projects.findByIdAndOrganizationId(projectB, organizationA))
            .thenReturn(Optional.empty());

        ProjectAccessService service = new ProjectAccessService(projects, members);

        assertThatThrownBy(() -> service.requireUpload(projectB, userA))
            .isInstanceOf(TenderNotFoundException.class);
        verify(projects).findByIdAndOrganizationId(projectB, organizationA);
    }
}
