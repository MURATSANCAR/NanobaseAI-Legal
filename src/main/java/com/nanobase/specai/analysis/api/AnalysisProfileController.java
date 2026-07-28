package com.nanobase.specai.analysis.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.analysis.api.AnalysisContracts.AnalysisProfileResponse;
import com.nanobase.specai.analysis.application.AnalysisProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analysis-profiles")
public class AnalysisProfileController {
    private final AnalysisProfileService profiles;
    private final ObjectMapper mapper;

    public AnalysisProfileController(AnalysisProfileService profiles, ObjectMapper mapper) {
        this.profiles = profiles;
        this.mapper = mapper;
    }

    @GetMapping
    List<AnalysisProfileResponse> list() {
        return profiles.list().stream()
            .map(profile -> AnalysisProfileResponse.from(profile, mapper)).toList();
    }

    @GetMapping("/{id}")
    AnalysisProfileResponse get(@PathVariable UUID id) {
        return AnalysisProfileResponse.from(profiles.get(id), mapper);
    }

    @PostMapping("/preview")
    PreviewResponse preview(@Valid @RequestBody PreviewRequest request) {
        AnalysisProfileService.ProfilePreview preview = profiles.preview(request.documentId());
        return new PreviewResponse(preview.snapshot(), preview.contentHash());
    }

    public record PreviewRequest(@NotNull UUID documentId) {
    }

    public record PreviewResponse(JsonNode snapshot, String contentHash) {
    }
}
