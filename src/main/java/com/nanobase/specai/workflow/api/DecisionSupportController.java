package com.nanobase.specai.workflow.api;

import com.nanobase.specai.workflow.api.DecisionContracts.CreateDecisionSupportRequest;
import com.nanobase.specai.workflow.api.DecisionContracts.DecisionSupportResponse;
import com.nanobase.specai.workflow.api.DecisionContracts.ExecutiveDecisionRequest;
import com.nanobase.specai.workflow.application.DecisionAndFinalizationService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DecisionSupportController {
    private final DecisionAndFinalizationService decisions;

    public DecisionSupportController(DecisionAndFinalizationService decisions) {
        this.decisions = decisions;
    }

    @PostMapping("/tenders/{projectId}/decision-support-cases")
    DecisionSupportResponse create(@PathVariable UUID projectId,
                                   @Valid @RequestBody
                                   CreateDecisionSupportRequest request) {
        return decisions.createCase(projectId, request);
    }

    @GetMapping("/tenders/{projectId}/decision-support-cases")
    java.util.List<DecisionSupportResponse> list(@PathVariable UUID projectId) {
        return decisions.cases(projectId);
    }

    @GetMapping("/decision-support-cases/{id}")
    DecisionSupportResponse get(@PathVariable UUID id) {
        return decisions.getCase(id);
    }

    @PostMapping("/decision-support-cases/{id}/decisions")
    Map<String, Object> decide(@PathVariable UUID id,
                               @Valid @RequestBody ExecutiveDecisionRequest request) {
        return decisions.decide(id, request);
    }

    @GetMapping("/decision-support-cases/{id}/explanation")
    DecisionSupportResponse explanation(@PathVariable UUID id) {
        return decisions.getCase(id);
    }
}
