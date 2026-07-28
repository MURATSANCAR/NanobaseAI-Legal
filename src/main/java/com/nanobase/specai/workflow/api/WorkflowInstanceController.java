package com.nanobase.specai.workflow.api;

import com.nanobase.specai.workflow.api.WorkflowContracts.StartWorkflowRequest;
import com.nanobase.specai.workflow.api.WorkflowContracts.WorkflowHistoryResponse;
import com.nanobase.specai.workflow.api.WorkflowContracts.WorkflowInstanceResponse;
import com.nanobase.specai.workflow.application.DynamicWorkflowService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflow-instances")
public class WorkflowInstanceController {
    private final DynamicWorkflowService workflows;

    public WorkflowInstanceController(DynamicWorkflowService workflows) {
        this.workflows = workflows;
    }

    @PostMapping
    WorkflowInstanceResponse start(@Valid @RequestBody StartWorkflowRequest request) {
        return workflows.start(request);
    }

    @GetMapping("/{id}")
    WorkflowInstanceResponse get(@PathVariable UUID id) {
        return workflows.instance(id);
    }

    @GetMapping("/{id}/history")
    WorkflowHistoryResponse history(@PathVariable UUID id) {
        return workflows.history(id);
    }

    @PostMapping("/{id}/cancel")
    WorkflowInstanceResponse cancel(@PathVariable UUID id) {
        return workflows.cancel(id);
    }

    @PostMapping("/{id}/retry")
    WorkflowInstanceResponse retry(@PathVariable UUID id) {
        return workflows.retry(id);
    }
}
