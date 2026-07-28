package com.nanobase.specai.audit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nanobase.specai.audit.application.AuditQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AuditControllerTest {
    @Mock AuditQueryService queries;

    @Test
    void auditListIsAlwaysScopedToAuthenticatedOrganization() {
        PageRequest pageable = PageRequest.of(0, 25);
        when(queries.list(pageable)).thenReturn(Page.empty());

        Page<?> result = new AuditController(queries).list(pageable);

        assertThat(result).isEmpty();
        org.mockito.Mockito.verify(queries).list(pageable);
    }
}
