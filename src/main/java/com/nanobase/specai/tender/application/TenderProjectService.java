package com.nanobase.specai.tender.application;

import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.tender.api.TenderContracts.CreateTenderRequest;
import com.nanobase.specai.tender.api.TenderContracts.TenderResponse;
import com.nanobase.specai.tender.api.TenderContracts.UpdateTenderRequest;
import com.nanobase.specai.tender.domain.ProjectMember;
import com.nanobase.specai.tender.domain.ProjectMemberRepository;
import com.nanobase.specai.tender.domain.TenderProject;
import com.nanobase.specai.tender.domain.TenderProjectRepository;
import com.nanobase.specai.tender.infrastructure.ProjectCodeGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenderProjectService {
    private final TenderProjectRepository projects;
    private final ProjectMemberRepository members;
    private final ProjectCodeGenerator codes;
    private final ProjectAccessService access;
    private final AuditService audit;
    private final CurrentTenant currentTenant;
    private final Clock clock;

    public TenderProjectService(TenderProjectRepository projects, ProjectMemberRepository members,
                                ProjectCodeGenerator codes, ProjectAccessService access,
                                AuditService audit, CurrentTenant currentTenant) {
        this(projects, members, codes, access, audit, currentTenant, Clock.systemUTC());
    }

    TenderProjectService(TenderProjectRepository projects, ProjectMemberRepository members,
                         ProjectCodeGenerator codes, ProjectAccessService access,
                         AuditService audit, CurrentTenant currentTenant, Clock clock) {
        this.projects = projects;
        this.members = members;
        this.codes = codes;
        this.access = access;
        this.audit = audit;
        this.currentTenant = currentTenant;
        this.clock = clock;
    }

    @Transactional
    public TenderResponse create(CreateTenderRequest request) {
        TenantPrincipal principal = currentTenant.require();
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        String code = codes.next();
        TenderProject project = TenderProject.create(id, principal.tenantId(), code, request.name(),
            request.institutionName(), request.tenderRegistrationNumber(), request.tenderType(),
            request.businessType(), request.sector(), request.priority(), request.bidDeadline(),
            request.clarificationDeadline(), request.description(), request.currency(),
            principal.subject(), now);
        projects.save(project);
        members.save(ProjectMember.owner(principal.tenantId(), id, principal.subject(), now));
        audit.record("TENDER_PROJECT_CREATED", "TenderProject", id, null,
            Map.of("projectCode", code));
        return TenderResponse.from(project);
    }

    @Transactional(readOnly = true)
    public TenderResponse get(UUID id) {
        TenantPrincipal principal = currentTenant.require();
        return TenderResponse.from(access.requireView(id, principal));
    }

    @Transactional(readOnly = true)
    public Page<TenderResponse> list(Pageable pageable) {
        TenantPrincipal principal = currentTenant.require();
        Page<TenderProject> result = access.tenantWide(principal)
            ? projects.findAllByOrganizationId(principal.tenantId(), pageable)
            : projects.findAccessible(principal.tenantId(), principal.subject(), pageable);
        return result.map(TenderResponse::from);
    }

    @Transactional
    public TenderResponse update(UUID id, UpdateTenderRequest request) {
        TenantPrincipal principal = currentTenant.require();
        TenderProject project = access.requireView(id, principal);
        if (request.version() != null && request.version() != project.version()) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
                TenderProject.class, id);
        }
        TenderResponse before = TenderResponse.from(project);
        project.update(request.name(), request.institutionName(),
            request.tenderRegistrationNumber(), request.tenderType(), request.businessType(),
            request.sector(), request.priority(), request.bidDeadline(),
            request.clarificationDeadline(), request.description(), request.currency(),
            clock.instant());
        TenderResponse after = TenderResponse.from(project);
        audit.record("TENDER_PROJECT_UPDATED", "TenderProject", id, before, after);
        return after;
    }

    @Transactional
    public TenderResponse archive(UUID id) {
        TenantPrincipal principal = currentTenant.require();
        TenderProject project = access.requireArchive(id, principal);
        TenderResponse before = TenderResponse.from(project);
        project.archive(clock.instant());
        TenderResponse after = TenderResponse.from(project);
        audit.record("TENDER_PROJECT_ARCHIVED", "TenderProject", id, before, after);
        return after;
    }
}
