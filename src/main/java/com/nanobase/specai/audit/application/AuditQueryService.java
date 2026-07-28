package com.nanobase.specai.audit.application;

import com.nanobase.specai.audit.domain.AuditEvent;
import com.nanobase.specai.audit.domain.AuditEventRepository;
import com.nanobase.specai.shared.security.CurrentTenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {
    private final AuditEventRepository events;
    private final CurrentTenant currentTenant;

    public AuditQueryService(AuditEventRepository events, CurrentTenant currentTenant) {
        this.events = events;
        this.currentTenant = currentTenant;
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> list(Pageable pageable) {
        return events.findAllByOrganizationId(
            currentTenant.require().tenantId(), pageable);
    }
}
