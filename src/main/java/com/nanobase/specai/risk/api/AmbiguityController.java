package com.nanobase.specai.risk.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.risk.application.RiskReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
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
public class AmbiguityController {
    private final RiskReviewService findings;

    public AmbiguityController(RiskReviewService findings) {
        this.findings = findings;
    }

    @GetMapping("/tenders/{projectId}/ambiguities")
    List<Map<String, Object>> list(@PathVariable UUID projectId) {
        return findings.ambiguities(projectId);
    }

    @GetMapping("/ambiguities/{id}")
    Map<String, Object> get(@PathVariable UUID id) {
        return findings.ambiguity(id);
    }

    @PostMapping("/ambiguities/{id}/review")
    Map<String, Object> review(@PathVariable UUID id,
                               @Valid @RequestBody ReviewRequest request) {
        return findings.reviewAmbiguity(id, request.reviewStatus(),
            request.feedbackType(), request.reason());
    }

    @PostMapping("/ambiguities/{id}/interpretations")
    Map<String, Object> interpretation(@PathVariable UUID id,
                                       @Valid @RequestBody InterpretationRequest request) {
        return findings.addInterpretation(id, request.interpretationText(),
            request.attributes(), request.confidence(), request.sourceIds());
    }

    @PostMapping("/ambiguities/{id}/clarification-candidates")
    Map<String, Object> clarification(@PathVariable UUID id,
                                      @Valid @RequestBody ClarificationRequest request) {
        return findings.clarification("AMBIGUITY", id, request.question(),
            request.reason(), request.sourceIds(), request.priorityConceptId(),
            request.requiresLegalReview());
    }

    public record ReviewRequest(
        @NotBlank @Size(max = 80) String reviewStatus,
        @NotBlank @Size(max = 160) String feedbackType,
        @Size(max = 4000) String reason
    ) {
    }

    public record InterpretationRequest(
        @NotBlank @Size(max = 10000) String interpretationText,
        @NotNull JsonNode attributes,
        @DecimalMin("0") @DecimalMax("1") double confidence,
        @NotNull JsonNode sourceIds
    ) {
    }

    public record ClarificationRequest(
        @NotBlank @Size(max = 10000) String question,
        @NotBlank @Size(max = 10000) String reason,
        @NotNull JsonNode sourceIds,
        @NotNull UUID priorityConceptId,
        boolean requiresLegalReview
    ) {
    }
}
