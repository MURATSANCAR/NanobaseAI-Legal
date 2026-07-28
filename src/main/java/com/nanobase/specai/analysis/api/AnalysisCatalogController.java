package com.nanobase.specai.analysis.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.analysis.application.AnalysisCatalogPort;
import com.nanobase.specai.shared.security.CurrentTenant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

@RestController
@RequestMapping("/api/v1")
public class AnalysisCatalogController {
    private final AnalysisCatalogPort catalog;
    private final CurrentTenant currentTenant;

    public AnalysisCatalogController(AnalysisCatalogPort catalog,
                                     CurrentTenant currentTenant) {
        this.catalog = catalog;
        this.currentTenant = currentTenant;
    }

    @GetMapping("/ontologies")
    List<Map<String, Object>> ontologies() {
        return catalog.ontologies(currentTenant.require().tenantId());
    }

    @GetMapping("/ontologies/{id}/versions")
    List<Map<String, Object>> ontologyVersions(@PathVariable UUID id) {
        return catalog.ontologyVersions(currentTenant.require().tenantId(), id);
    }

    @GetMapping("/ontology-versions/{id}/concepts")
    List<Map<String, Object>> ontologyConcepts(@PathVariable UUID id) {
        return catalog.ontologyConcepts(currentTenant.require().tenantId(), id);
    }

    @GetMapping("/terminology-catalogs")
    List<Map<String, Object>> terminologyCatalogs() {
        return catalog.terminologyCatalogs(currentTenant.require().tenantId());
    }

    @PostMapping("/terminology-catalogs/{id}/candidate-terms")
    @ResponseStatus(HttpStatus.CREATED)
    CandidateResponse candidate(@PathVariable UUID id,
                                @Valid @RequestBody CandidateRequest request) {
        UUID entryId = catalog.createCandidateTerm(currentTenant.require().tenantId(), id,
            request.term(), request.termType(), request.semanticRole(),
            request.weight(), request.metadata());
        return new CandidateResponse(entryId, "CANDIDATE", false);
    }

    @PostMapping("/terminology-entries/{id}/approve")
    CandidateResponse approve(@PathVariable UUID id) {
        catalog.decideCandidateTerm(currentTenant.require().tenantId(), id, true);
        return new CandidateResponse(id, "APPROVED", true);
    }

    @PostMapping("/terminology-entries/{id}/reject")
    CandidateResponse reject(@PathVariable UUID id) {
        catalog.decideCandidateTerm(currentTenant.require().tenantId(), id, false);
        return new CandidateResponse(id, "REJECTED", false);
    }

    public record CandidateRequest(
        @NotBlank @Size(max = 500) String term,
        @NotBlank @Size(max = 120) String termType,
        @Size(max = 160) String semanticRole,
        @DecimalMin("0.0") @DecimalMax("100.0") double weight,
        @NotNull JsonNode metadata
    ) {
    }

    public record CandidateResponse(UUID id, String status, boolean active) {
    }
}
