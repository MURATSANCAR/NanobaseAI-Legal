package com.nanobase.specai.companyfit.api;

import com.nanobase.specai.companyfit.application.CompanyFitService;
import com.nanobase.specai.shared.security.CurrentTenant;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class CompanyFitController {
    private final CompanyFitService service;
    private final CurrentTenant currentTenant;

    public CompanyFitController(CompanyFitService service, CurrentTenant currentTenant) {
        this.service = service;
        this.currentTenant = currentTenant;
    }

    @PostMapping("/organizations/{orgId}/capabilities/ingest")
    @ResponseStatus(HttpStatus.OK)
    Map<String, Object> ingest(@PathVariable UUID orgId, @RequestBody IngestRequest body) {
        var principal = currentTenant.require();
        requireOrg(principal.tenantId(), orgId);
        List<Map<String, Object>> docs = body.documents() == null ? List.of() : body.documents();
        return service.ingest(orgId, docs);
    }

    @GetMapping("/organizations/{orgId}/capabilities")
    List<Map<String, Object>> listCapabilities(@PathVariable UUID orgId) {
        var principal = currentTenant.require();
        requireOrg(principal.tenantId(), orgId);
        return service.listCapabilities(orgId);
    }

    @PostMapping("/tenders/{documentId}/company-fit")
    @ResponseStatus(HttpStatus.OK)
    Map<String, Object> companyFit(@PathVariable UUID documentId,
                                   @RequestBody FitRequest body) {
        var principal = currentTenant.require();
        UUID orgId = body.organizationId() != null ? body.organizationId() : principal.tenantId();
        requireOrg(principal.tenantId(), orgId);
        return service.evaluate(orgId, documentId,
            body.requirements() == null ? List.of() : body.requirements(),
            body.capabilities() == null ? List.of() : body.capabilities());
    }

    @GetMapping("/tenders/{documentId}/company-fit")
    List<Map<String, Object>> latest(@PathVariable UUID documentId) {
        var principal = currentTenant.require();
        return service.latestFit(principal.tenantId(), documentId);
    }

    private static void requireOrg(UUID tenantId, UUID orgId) {
        if (!tenantId.equals(orgId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "organization mismatch");
        }
    }

    public record IngestRequest(@NotNull List<Map<String, Object>> documents) {
    }

    public record FitRequest(
        UUID organizationId,
        List<Map<String, Object>> requirements,
        List<Map<String, Object>> capabilities
    ) {
    }
}
