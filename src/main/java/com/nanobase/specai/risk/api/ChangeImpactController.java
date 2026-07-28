package com.nanobase.specai.risk.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.risk.application.ChangeImpactService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ChangeImpactController {
    private final ChangeImpactService changes;

    public ChangeImpactController(ChangeImpactService changes) {
        this.changes = changes;
    }

    @PostMapping("/documents/{documentId}/change-sets")
    Map<String, Object> create(@PathVariable UUID documentId,
                               @Valid @RequestBody CreateChangeSetRequest request) {
        return changes.create(documentId, request.baseDocumentVersionId(),
            request.targetDocumentVersionId());
    }

    @GetMapping("/change-sets/{id}")
    Map<String, Object> get(@PathVariable UUID id) {
        return changes.changeSet(id);
    }

    @GetMapping("/change-sets/{id}/items")
    List<Map<String, Object>> items(@PathVariable UUID id) {
        return changes.items(id);
    }

    @PutMapping("/change-sets/{changeSetId}/items/{itemId}")
    Map<String, Object> correct(@PathVariable UUID changeSetId,
                                @PathVariable UUID itemId,
                                @Valid @RequestBody CorrectMatchRequest request) {
        return changes.correct(changeSetId, itemId, request.baseClauseId(),
            request.targetClauseId(), request.changeTypeConceptId(),
            request.reviewStatus(), request.attributes());
    }

    @PostMapping("/change-sets/{id}/impact-analyses")
    Map<String, Object> analyze(@PathVariable UUID id) {
        return changes.analyze(id);
    }

    @GetMapping("/impact-analyses/{id}")
    Map<String, Object> impact(@PathVariable UUID id) {
        return changes.impact(id);
    }

    @GetMapping("/impact-analyses/{id}/events")
    List<Map<String, Object>> events(@PathVariable UUID id) {
        return changes.impactEvents(id);
    }

    public record CreateChangeSetRequest(
        @NotNull UUID baseDocumentVersionId,
        @NotNull UUID targetDocumentVersionId
    ) {
    }

    public record CorrectMatchRequest(
        UUID baseClauseId,
        UUID targetClauseId,
        @NotNull UUID changeTypeConceptId,
        @NotBlank @Size(max = 80) String reviewStatus,
        @NotNull JsonNode attributes
    ) {
    }
}
