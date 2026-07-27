package com.nanobase.specai.tender.application;

import com.nanobase.specai.audit.domain.AuditEvent;
import com.nanobase.specai.audit.domain.AuditEventRepository;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.tender.api.TenderContracts.CreateTenderRequest;
import com.nanobase.specai.tender.api.TenderContracts.TenderResponse;
import com.nanobase.specai.tender.api.TenderContracts.UpdateTenderRequest;
import com.nanobase.specai.tender.domain.TenderProject;
import com.nanobase.specai.tender.domain.TenderProjectRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenderProjectService {
    private final TenderProjectRepository projects;
    private final AuditEventRepository auditEvents;
    private final CurrentTenant currentTenant;
    private final Clock clock;

    public TenderProjectService(TenderProjectRepository projects, AuditEventRepository auditEvents,
                                CurrentTenant currentTenant) {
        this(projects, auditEvents, currentTenant, Clock.systemUTC());
    }

    TenderProjectService(TenderProjectRepository projects, AuditEventRepository auditEvents,
                         CurrentTenant currentTenant, Clock clock) {
        this.projects = projects;
        this.auditEvents = auditEvents;
        this.currentTenant = currentTenant;
        this.clock = clock;
    }

    @Transactional
    public TenderResponse create(CreateTenderRequest request) {
        TenantPrincipal principal = currentTenant.require();
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        String code = "TND-%d-%s".formatted(now.atZone(ZoneOffset.UTC).getYear(),
            id.toString().substring(0, 8).toUpperCase());
        TenderProject project = TenderProject.create(id, principal.tenantId(), code, request.name(),
            request.contractingAuthority(), request.registrationNumber(), request.deadline(),
            request.currency(), request.priority(), request.description(), principal.subject(), now);
        projects.save(project);
        auditEvents.save(new AuditEvent(UUID.randomUUID(), principal.tenantId(), principal.subject(),
            "TENDER_CREATED", "TenderProject", id, now, "{\"code\":\"%s\"}".formatted(code)));
        return TenderResponse.from(project);
    }

    @Transactional(readOnly = true)
    public TenderResponse get(UUID id) {
        TenantPrincipal principal = currentTenant.require();
        return projects.findByIdAndTenantId(id, principal.tenantId())
            .map(TenderResponse::from)
            .orElseThrow(() -> new TenderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<TenderResponse> list(Pageable pageable) {
        return projects.findAllByTenantId(currentTenant.require().tenantId(), pageable)
            .map(TenderResponse::from);
    }

    @Transactional
    public TenderResponse update(UUID id, UpdateTenderRequest request) {
        TenantPrincipal principal = currentTenant.require();
        TenderProject project = projects.findByIdAndTenantId(id, principal.tenantId())
            .orElseThrow(() -> new TenderNotFoundException(id));
        Instant now = clock.instant();
        project.update(request.name(), request.contractingAuthority(), request.registrationNumber(),
            request.deadline(), request.currency(), request.priority(), request.description(), now);
        auditEvents.save(new AuditEvent(UUID.randomUUID(), principal.tenantId(), principal.subject(),
            "TENDER_UPDATED", "TenderProject", id, now, "{\"version\":%d}".formatted(project.version())));
        return TenderResponse.from(project);
    }
}
