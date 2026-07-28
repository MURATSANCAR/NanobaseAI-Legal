package com.nanobase.specai.compliance.api;

import com.nanobase.specai.compliance.api.ComplianceContracts.EvidenceLinkRequest;
import com.nanobase.specai.compliance.api.ComplianceContracts.ReviewRequest;
import com.nanobase.specai.compliance.application.ComplianceEvaluationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ComplianceEvaluationController {
    private final ComplianceEvaluationService evaluations;

    public ComplianceEvaluationController(ComplianceEvaluationService evaluations) {
        this.evaluations = evaluations;
    }

    @GetMapping("/tenders/{projectId}/compliance-evaluations")
    List<Map<String, Object>> list(@PathVariable UUID projectId) {
        return evaluations.list(projectId);
    }

    @GetMapping("/compliance-evaluations/{id}")
    Map<String, Object> get(@PathVariable UUID id) {
        return evaluations.get(id);
    }

    @PostMapping("/compliance-evaluations/{id}/review")
    Map<String, Object> review(@PathVariable UUID id,
                               @Valid @RequestBody ReviewRequest request) {
        return evaluations.review(id, request);
    }

    @PostMapping("/compliance-evaluations/{id}/evidence")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> evidence(@PathVariable UUID id,
                                 @Valid @RequestBody EvidenceLinkRequest request) {
        return evaluations.addEvidence(id, request);
    }

    @DeleteMapping("/compliance-evaluations/{id}/evidence/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteEvidence(@PathVariable UUID id, @PathVariable UUID linkId) {
        evaluations.removeEvidence(id, linkId);
    }

    @GetMapping("/compliance-evaluations/{id}/history")
    List<Map<String, Object>> history(@PathVariable UUID id) {
        return evaluations.history(id);
    }

    @GetMapping("/compliance-evaluations/{id}/explanation")
    Map<String, Object> explanation(@PathVariable UUID id) {
        return evaluations.explanation(id);
    }
}
