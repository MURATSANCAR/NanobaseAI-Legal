package com.nanobase.specai.risk.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.risk.application.RiskReviewService;
import jakarta.validation.Valid;
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
public class ConflictController {
    private final RiskReviewService conflicts;

    public ConflictController(RiskReviewService conflicts) {
        this.conflicts = conflicts;
    }

    @GetMapping("/tenders/{projectId}/conflicts")
    List<Map<String, Object>> list(@PathVariable UUID projectId) {
        return conflicts.conflicts(projectId);
    }

    @GetMapping("/conflicts/{id}")
    Map<String, Object> get(@PathVariable UUID id) {
        return conflicts.conflict(id);
    }

    @PostMapping("/conflicts/{id}/review")
    Map<String, Object> review(@PathVariable UUID id,
                               @Valid @RequestBody ReviewRequest request) {
        return conflicts.reviewConflict(id, request.reviewStatus(),
            request.feedbackType(), request.reason(), request.resolution());
    }

    @PostMapping("/conflicts/{id}/resolve")
    Map<String, Object> resolve(@PathVariable UUID id,
                                @Valid @RequestBody ReviewRequest request) {
        return conflicts.reviewConflict(id, request.reviewStatus(),
            request.feedbackType(), request.reason(), request.resolution());
    }

    @PostMapping("/conflicts/{id}/clarification-candidates")
    Map<String, Object> clarification(@PathVariable UUID id,
                                      @Valid @RequestBody ClarificationRequest request) {
        return conflicts.clarification("CONFLICT", id, request.question(),
            request.reason(), request.sourceIds(), request.priorityConceptId(),
            request.requiresLegalReview());
    }

    @GetMapping("/conflicts/{id}/history")
    Object history(@PathVariable UUID id) {
        return conflicts.conflict(id).get("history");
    }

    @GetMapping("/conflicts/{id}/explanation")
    Map<String, Object> explanation(@PathVariable UUID id) {
        return conflicts.conflictExplanation(id);
    }

    public record ReviewRequest(
        @NotBlank @Size(max = 80) String reviewStatus,
        @NotBlank @Size(max = 160) String feedbackType,
        @Size(max = 4000) String reason,
        @NotNull JsonNode resolution
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
