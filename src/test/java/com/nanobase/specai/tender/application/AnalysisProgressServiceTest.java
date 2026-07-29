package com.nanobase.specai.tender.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.tender.api.TenderContracts.AnalysisProgressResponse;
import com.nanobase.specai.tender.domain.TenderProject;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AnalysisProgressServiceTest {
    @Mock
    JdbcTemplate jdbc;
    @Mock
    ProjectAccessService access;
    @Mock
    CurrentTenant currentTenant;
    @Mock
    TenderProject project;

    AnalysisProgressService service;
    UUID orgId;
    UUID projectId;
    TenantPrincipal principal;

    @BeforeEach
    void setUp() {
        service = new AnalysisProgressService(jdbc, access, currentTenant);
        orgId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        principal = new TenantPrincipal(orgId, "user-1", Set.of("TENDER_MANAGER"));
        when(currentTenant.require()).thenReturn(principal);
        when(access.requireView(projectId, principal)).thenReturn(project);
    }

    @Test
    void recommendsFirstIncompleteStep() {
        when(jdbc.queryForObject(any(String.class), eq(Long.class), eq(orgId), eq(projectId)))
            .thenReturn(1L, 0L, 0L, 0L);
        when(jdbc.queryForObject(any(String.class), eq(Long.class), eq(orgId)))
            .thenReturn(0L);

        AnalysisProgressResponse response = service.progress(projectId);

        assertThat(response.documents()).isTrue();
        assertThat(response.requirements()).isFalse();
        assertThat(response.recommendedStep()).isEqualTo("requirements");
        assertThat(response.counts().readyDocuments()).isEqualTo(1L);
        verify(access).requireView(projectId, principal);
    }
}
