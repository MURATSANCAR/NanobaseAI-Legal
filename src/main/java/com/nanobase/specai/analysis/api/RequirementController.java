package com.nanobase.specai.analysis.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.analysis.api.AnalysisContracts.RequirementResponse;
import com.nanobase.specai.analysis.api.AnalysisContracts.RevisionResponse;
import com.nanobase.specai.analysis.application.RequirementService;
import com.nanobase.specai.analysis.application.RequirementService.EditCommand;
import com.nanobase.specai.analysis.application.RequirementService.MergeCommand;
import com.nanobase.specai.analysis.application.RequirementService.ReviewCommand;
import com.nanobase.specai.analysis.application.RequirementService.SplitCommand;
import com.nanobase.specai.analysis.application.RequirementService.SplitPart;
import com.nanobase.specai.analysis.domain.Requirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RequirementController {
    private final RequirementService requirements;
    private final ObjectMapper mapper;

    public RequirementController(RequirementService requirements, ObjectMapper mapper) {
        this.requirements = requirements;
        this.mapper = mapper;
    }

    @GetMapping("/tenders/{projectId}/requirements")
    Page<RequirementResponse> list(
        @PathVariable UUID projectId,
        @PageableDefault(size = 50, sort = "createdAt") Pageable pageable) {
        return requirements.list(projectId, pageable)
            .map(requirement -> RequirementResponse.from(requirement, mapper));
    }

    @GetMapping("/requirements/{id}")
    RequirementResponse get(@PathVariable UUID id) {
        return RequirementResponse.from(requirements.get(id), mapper);
    }

    @PutMapping("/requirements/{id}")
    RequirementResponse update(@PathVariable UUID id,
                               @Valid @RequestBody EditRequest request) {
        Requirement saved = requirements.update(id, new EditCommand(
            request.requirementCode(), request.title(), request.requirementText(),
            request.primaryConceptId(), request.modality(), request.modalityConceptId(),
            request.testabilityStatus(), request.conditionText(), request.subjectText(),
            request.actionText(), request.objectText(), request.attributes(),
            request.sourceFragments(), request.reviewStatus(), request.feedbackType(),
            request.reason(), request.approvedForLearning()));
        return RequirementResponse.from(saved, mapper);
    }

    @PostMapping("/requirements/{id}/review")
    RequirementResponse review(@PathVariable UUID id,
                               @Valid @RequestBody ReviewRequest request) {
        return RequirementResponse.from(requirements.review(id,
            new ReviewCommand(request.reviewStatus(), request.feedbackType(),
                request.reason(), request.approvedForLearning())), mapper);
    }

    @PostMapping("/requirements/{id}/split")
    List<RequirementResponse> split(@PathVariable UUID id,
                                    @Valid @RequestBody SplitRequest request) {
        List<SplitPart> parts = request.parts().stream().map(part -> new SplitPart(
            part.requirementCode(), part.title(), part.requirementText(),
            part.primaryConceptId(), part.modality(), part.modalityConceptId(),
            part.testabilityStatus(), part.conditionText(), part.subjectText(),
            part.actionText(), part.objectText(), part.attributes(),
            part.sourceFragments())).toList();
        return requirements.split(id, new SplitCommand(parts, request.reason(),
                request.approvedForLearning())).stream()
            .map(requirement -> RequirementResponse.from(requirement, mapper)).toList();
    }

    @PostMapping("/requirements/{id}/merge")
    RequirementResponse merge(@PathVariable UUID id,
                               @Valid @RequestBody MergeRequest request) {
        Requirement merged = requirements.merge(id, new MergeCommand(
            request.requirementIds(), request.requirementCode(), request.title(),
            request.requirementText(), request.primaryConceptId(), request.modality(),
            request.modalityConceptId(), request.testabilityStatus(),
            request.conditionText(), request.subjectText(), request.actionText(),
            request.objectText(), request.attributes(), request.reason(),
            request.approvedForLearning()));
        return RequirementResponse.from(merged, mapper);
    }

    @GetMapping("/requirements/{id}/revisions")
    List<RevisionResponse> revisions(@PathVariable UUID id) {
        return requirements.revisions(id).stream()
            .map(revision -> RevisionResponse.from(revision, mapper)).toList();
    }

    @GetMapping("/requirements/{id}/explanation")
    Map<String, Object> explanation(@PathVariable UUID id) {
        Requirement requirement = requirements.get(id);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("requirementId", requirement.id());
        response.put("sourceClauseId", requirement.sourceClauseId());
        response.put("analysisProfileId", requirement.analysisProfileId());
        response.put("ontologyVersionId", requirement.ontologyVersionId());
        response.put("policyVersionId", requirement.policyVersionId());
        response.put("promptPackageVersionId", requirement.promptVersionId());
        response.put("groundingStatus", requirement.groundingStatus());
        response.put("combinedConfidence", requirement.combinedConfidence());
        response.put("details",
            AnalysisContracts.json(mapper, requirement.explanationJson()));
        response.put("sourceFragments", requirements.sourceFragments(id));
        response.put("revisions", requirements.revisions(id).stream()
            .map(revision -> RevisionResponse.from(revision, mapper)).toList());
        return Map.copyOf(response);
    }

    public record EditRequest(
        @Size(max = 160) String requirementCode,
        @Size(max = 500) String title,
        @NotBlank String requirementText,
        UUID primaryConceptId,
        @Size(max = 160) String modality,
        UUID modalityConceptId,
        @Size(max = 160) String testabilityStatus,
        String conditionText, String subjectText, String actionText, String objectText,
        @NotNull JsonNode attributes,
        List<@NotBlank String> sourceFragments,
        @Size(max = 80) String reviewStatus,
        @Size(max = 160) String feedbackType,
        @Size(max = 4000) String reason,
        boolean approvedForLearning
    ) {
    }

    public record ReviewRequest(
        @NotBlank @Size(max = 80) String reviewStatus,
        @NotBlank @Size(max = 160) String feedbackType,
        @Size(max = 4000) String reason,
        boolean approvedForLearning
    ) {
    }

    public record SplitRequest(
        @NotEmpty @Size(min = 2) List<@Valid SplitPartRequest> parts,
        @Size(max = 4000) String reason,
        boolean approvedForLearning
    ) {
    }

    public record SplitPartRequest(
        @NotBlank @Size(max = 160) String requirementCode,
        @Size(max = 500) String title,
        @NotBlank String requirementText,
        UUID primaryConceptId,
        @Size(max = 160) String modality,
        UUID modalityConceptId,
        @Size(max = 160) String testabilityStatus,
        String conditionText, String subjectText, String actionText, String objectText,
        @NotNull JsonNode attributes,
        @NotEmpty List<@NotBlank String> sourceFragments
    ) {
    }

    public record MergeRequest(
        @NotEmpty List<@NotNull UUID> requirementIds,
        @NotBlank @Size(max = 160) String requirementCode,
        @Size(max = 500) String title,
        @NotBlank String requirementText,
        UUID primaryConceptId,
        @Size(max = 160) String modality,
        UUID modalityConceptId,
        @Size(max = 160) String testabilityStatus,
        String conditionText, String subjectText, String actionText, String objectText,
        @NotNull JsonNode attributes,
        @Size(max = 4000) String reason,
        boolean approvedForLearning
    ) {
    }
}
