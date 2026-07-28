package com.nanobase.specai.workflow.api;

import com.nanobase.specai.workflow.api.ClarificationContracts.ClarificationAnswerRequest;
import com.nanobase.specai.workflow.api.ClarificationContracts.ClarificationResponse;
import com.nanobase.specai.workflow.api.ClarificationContracts.ClarificationStatusRequest;
import com.nanobase.specai.workflow.api.ClarificationContracts.RevisionRequest;
import com.nanobase.specai.workflow.application.ClarificationWorkflowService;
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
public class ClarificationController {
    private final ClarificationWorkflowService clarifications;

    public ClarificationController(ClarificationWorkflowService clarifications) {
        this.clarifications = clarifications;
    }

    @GetMapping("/tenders/{projectId}/clarifications")
    Map<String, Object> list(@PathVariable UUID projectId) {
        return clarifications.list(projectId);
    }

    @PostMapping("/clarifications/{id}/revisions")
    ClarificationResponse revise(@PathVariable UUID id,
                                 @Valid @RequestBody RevisionRequest request) {
        return clarifications.revise(id, request);
    }

    @PostMapping("/clarifications/{id}/submit-review")
    ClarificationResponse review(@PathVariable UUID id,
                                 @Valid @RequestBody
                                 ClarificationStatusRequest request) {
        return clarifications.changeStatus(id, request.targetStatusConceptId(), "review");
    }

    @PostMapping("/clarifications/{id}/approve")
    ClarificationResponse approve(@PathVariable UUID id,
                                  @Valid @RequestBody
                                  ClarificationStatusRequest request) {
        return clarifications.changeStatus(id, request.targetStatusConceptId(), "approve");
    }

    @PostMapping("/clarifications/{id}/mark-sent")
    ClarificationResponse sent(@PathVariable UUID id,
                               @Valid @RequestBody
                               ClarificationStatusRequest request) {
        return clarifications.changeStatus(id, request.targetStatusConceptId(), "send");
    }

    @PostMapping("/clarifications/{id}/answers")
    ClarificationResponse answer(@PathVariable UUID id,
                                 @Valid @RequestBody ClarificationAnswerRequest request) {
        return clarifications.answer(id, request);
    }
}
