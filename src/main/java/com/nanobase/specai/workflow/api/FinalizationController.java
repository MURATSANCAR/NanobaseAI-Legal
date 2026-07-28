package com.nanobase.specai.workflow.api;

import com.nanobase.specai.workflow.api.DecisionContracts.FinalizeProjectRequest;
import com.nanobase.specai.workflow.api.DecisionContracts.ReopenProjectRequest;
import com.nanobase.specai.workflow.application.DecisionAndFinalizationService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/tenders/{projectId}")
public class FinalizationController {
    private final DecisionAndFinalizationService finalization;

    public FinalizationController(DecisionAndFinalizationService finalization) {
        this.finalization = finalization;
    }

    @PostMapping("/finalize")
    Map<String, Object> finalizeProject(@PathVariable UUID projectId,
                                        @Valid @RequestBody
                                        FinalizeProjectRequest request) {
        return finalization.finalizeProject(projectId, request);
    }

    @PostMapping("/reopen")
    Map<String, Object> reopen(@PathVariable UUID projectId,
                               @Valid @RequestBody ReopenProjectRequest request) {
        return finalization.reopen(projectId, request);
    }

    @GetMapping("/finalization-history")
    List<Map<String, Object>> history(@PathVariable UUID projectId) {
        return finalization.history(projectId);
    }
}
