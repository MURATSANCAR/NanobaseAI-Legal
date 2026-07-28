package com.nanobase.specai.knowledge.api;

import com.nanobase.specai.knowledge.api.KnowledgeContracts.EvidenceAssessmentRequest;
import com.nanobase.specai.knowledge.application.EvidenceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/evidence")
public class EvidenceController {
    private final EvidenceService evidence;

    public EvidenceController(EvidenceService evidence) {
        this.evidence = evidence;
    }

    @GetMapping
    List<Map<String, Object>> list(
        @RequestParam(required = false) UUID documentId,
        @RequestParam(required = false) String validityStatus
    ) {
        return evidence.list(documentId, validityStatus);
    }

    @GetMapping("/{id}")
    Map<String, Object> get(@PathVariable UUID id) {
        return evidence.get(id);
    }

    @PostMapping("/{id}/verify")
    Map<String, Object> verify(@PathVariable UUID id,
                               @Valid @RequestBody EvidenceAssessmentRequest request) {
        return evidence.assess(id, request, false);
    }

    @PostMapping("/{id}/invalidate")
    Map<String, Object> invalidate(@PathVariable UUID id,
                                   @Valid @RequestBody EvidenceAssessmentRequest request) {
        return evidence.assess(id, request, true);
    }

    @GetMapping("/{id}/usages")
    List<Map<String, Object>> usages(@PathVariable UUID id) {
        return evidence.usages(id);
    }
}
