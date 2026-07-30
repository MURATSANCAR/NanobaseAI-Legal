package com.nanobase.specai.obligation.api;

import com.nanobase.specai.obligation.application.ObligationGenerationService;
import com.nanobase.specai.shared.security.CurrentTenant;
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
@RequestMapping("/api/v1/contracts/{contractId}/obligations")
public class ObligationController {
    private final ObligationGenerationService service;
    private final CurrentTenant currentTenant;

    public ObligationController(ObligationGenerationService service, CurrentTenant currentTenant) {
        this.service = service;
        this.currentTenant = currentTenant;
    }

    @GetMapping
    List<Map<String, Object>> list(@PathVariable UUID contractId) {
        var principal = currentTenant.require();
        return service.listObligations(principal.tenantId(), contractId);
    }

    @PostMapping("/{obligationId}/evidence")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> submitEvidence(@PathVariable UUID contractId,
                                       @PathVariable UUID obligationId,
                                       @RequestBody EvidenceRequest request) {
        var principal = currentTenant.require();
        UUID id = service.submitEvidence(principal.tenantId(), obligationId,
            request.documentId(), request.evidenceFragmentId(), principal.subject(),
            request.comment());
        return Map.of("id", id, "contractId", contractId, "obligationId", obligationId);
    }

    public record EvidenceRequest(
        UUID documentId,
        UUID evidenceFragmentId,
        String comment
    ) {
    }
}
