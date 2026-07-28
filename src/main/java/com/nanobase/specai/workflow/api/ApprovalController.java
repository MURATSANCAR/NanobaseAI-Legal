package com.nanobase.specai.workflow.api;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nanobase.specai.workflow.api.WorkManagementContracts.ApprovalDecisionRequest;
import com.nanobase.specai.workflow.api.WorkManagementContracts.ApprovalDecisionResponse;
import com.nanobase.specai.workflow.api.WorkManagementContracts.ApprovalResponse;
import com.nanobase.specai.workflow.application.WorkManagementService;
import com.nanobase.specai.workflow.application.DynamicWorkflowService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {
    private final WorkManagementService approvals;
    private final DynamicWorkflowService workflows;

    public ApprovalController(WorkManagementService approvals,
                              DynamicWorkflowService workflows) {
        this.approvals = approvals;
        this.workflows = workflows;
    }

    @GetMapping
    List<ApprovalResponse> list(@RequestParam(required = false) UUID projectId) {
        return approvals.approvals(projectId);
    }

    @GetMapping("/{id}")
    ApprovalResponse get(@PathVariable UUID id) {
        return approvals.approval(id);
    }

    @PostMapping("/{id}/decisions")
    ApprovalDecisionResponse decide(@PathVariable UUID id,
                                    @Valid @RequestBody ApprovalDecisionRequest request) {
        ApprovalDecisionResponse decision = approvals.decide(id, request);
        ApprovalResponse approval = approvals.approval(id);
        if (approval.completedAt() != null && approval.workflowExecutionId() != null) {
            var output = JsonNodeFactory.instance.objectNode();
            output.put("approvalRequestId", id.toString());
            output.put("decisionConceptId", decision.decisionConceptId().toString());
            workflows.resume(approval.workflowExecutionId(), output);
        }
        return decision;
    }

    @GetMapping("/{id}/history")
    List<ApprovalDecisionResponse> history(@PathVariable UUID id) {
        return approvals.decisions(id);
    }
}
