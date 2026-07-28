package com.nanobase.specai.workflow.api;

import com.nanobase.specai.workflow.api.WorkflowContracts.CreateWorkflowRequest;
import com.nanobase.specai.workflow.api.WorkflowContracts.CreateWorkflowVersionRequest;
import com.nanobase.specai.workflow.api.WorkflowContracts.SimulationRequest;
import com.nanobase.specai.workflow.api.WorkflowContracts.SimulationResponse;
import com.nanobase.specai.workflow.api.WorkflowContracts.WorkflowDefinitionResponse;
import com.nanobase.specai.workflow.api.WorkflowContracts.WorkflowVersionResponse;
import com.nanobase.specai.workflow.application.DynamicWorkflowService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WorkflowController {
    private final DynamicWorkflowService workflows;

    public WorkflowController(DynamicWorkflowService workflows) {
        this.workflows = workflows;
    }

    @PostMapping("/workflows")
    WorkflowDefinitionResponse create(@Valid @RequestBody CreateWorkflowRequest request) {
        return workflows.create(request);
    }

    @GetMapping("/workflows")
    List<WorkflowDefinitionResponse> list() {
        return workflows.list();
    }

    @GetMapping("/workflows/{id}")
    WorkflowDefinitionResponse get(@PathVariable UUID id) {
        return workflows.get(id);
    }

    @PostMapping("/workflows/{id}/versions")
    WorkflowVersionResponse createVersion(@PathVariable UUID id,
                                          @Valid @RequestBody
                                          CreateWorkflowVersionRequest request) {
        return workflows.createVersion(id, request);
    }

    @PostMapping("/workflow-versions/{id}/validate")
    SimulationResponse validate(@PathVariable UUID id) {
        return workflows.validate(id);
    }

    @PostMapping("/workflow-versions/{id}/simulate")
    SimulationResponse simulate(@PathVariable UUID id,
                                @RequestBody SimulationRequest request) {
        return workflows.simulate(id, request);
    }

    @PostMapping("/workflow-versions/{id}/activate")
    WorkflowVersionResponse activate(@PathVariable UUID id) {
        return workflows.activate(id);
    }
}
