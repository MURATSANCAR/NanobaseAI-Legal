package com.nanobase.specai.risk.api;

import com.nanobase.specai.risk.application.RiskReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RiskController {
    private final RiskReviewService risks;

    public RiskController(RiskReviewService risks) {
        this.risks = risks;
    }

    @GetMapping("/tenders/{projectId}/risks")
    List<Map<String, Object>> list(@PathVariable UUID projectId) {
        return risks.risks(projectId);
    }

    @GetMapping("/risks/{id}")
    Map<String, Object> get(@PathVariable UUID id) {
        return risks.risk(id);
    }

    @PutMapping("/risks/{id}")
    Map<String, Object> update(@PathVariable UUID id,
                               @Valid @RequestBody UpdateRiskRequest request) {
        return risks.updateRisk(id, request.title(), request.description(),
            request.probabilityScore(), request.impactScore(), request.exposureScore(),
            request.severityConceptId(), request.statusConceptId(),
            request.feedbackType(), request.reason());
    }

    @PostMapping("/risks/{id}/review")
    Map<String, Object> review(@PathVariable UUID id,
                               @Valid @RequestBody ReviewRequest request) {
        return risks.reviewRisk(id, request.reviewStatus(), request.feedbackType(),
            request.reason(), request.approvedForLearning());
    }

    @PostMapping("/risks/{id}/assign")
    Map<String, Object> assign(@PathVariable UUID id,
                               @Valid @RequestBody AssignRequest request) {
        return risks.assignRisk(id, request.ownerUserId(), request.dueDate());
    }

    @PostMapping("/risks/{id}/mitigations")
    List<Map<String, Object>> mitigation(@PathVariable UUID id,
                                         @Valid @RequestBody MitigationRequest request) {
        return risks.chooseMitigation(id, request.candidateId(), request.reviewStatus());
    }

    @GetMapping("/risks/{id}/history")
    Object history(@PathVariable UUID id) {
        return risks.risk(id).get("history");
    }

    @GetMapping("/risks/{id}/explanation")
    Map<String, Object> explanation(@PathVariable UUID id) {
        return risks.riskExplanation(id);
    }

    @GetMapping("/risks/{id}/propagation")
    List<Map<String, Object>> propagation(@PathVariable UUID id,
                                          @RequestParam(defaultValue = "false")
                                          boolean refresh) {
        return risks.propagation(id, refresh);
    }

    public record UpdateRiskRequest(
        @Size(max = 500) String title,
        @Size(max = 10000) String description,
        @DecimalMin("0") @DecimalMax("1") Double probabilityScore,
        @DecimalMin("0") @DecimalMax("1") Double impactScore,
        @DecimalMin("0") @DecimalMax("1") Double exposureScore,
        UUID severityConceptId,
        UUID statusConceptId,
        @Size(max = 160) String feedbackType,
        @Size(max = 4000) String reason
    ) {
    }

    public record ReviewRequest(
        @NotBlank @Size(max = 80) String reviewStatus,
        @NotBlank @Size(max = 160) String feedbackType,
        @Size(max = 4000) String reason,
        boolean approvedForLearning
    ) {
    }

    public record AssignRequest(
        @NotBlank @Size(max = 255) String ownerUserId,
        LocalDate dueDate
    ) {
    }

    public record MitigationRequest(
        @NotNull UUID candidateId,
        @NotBlank @Size(max = 80) String reviewStatus
    ) {
    }
}
