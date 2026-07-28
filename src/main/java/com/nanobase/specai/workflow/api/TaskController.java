package com.nanobase.specai.workflow.api;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nanobase.specai.workflow.api.WorkManagementContracts.AddCommentRequest;
import com.nanobase.specai.workflow.api.WorkManagementContracts.AssignTaskRequest;
import com.nanobase.specai.workflow.api.WorkManagementContracts.EscalateTaskRequest;
import com.nanobase.specai.workflow.api.WorkManagementContracts.TaskResponse;
import com.nanobase.specai.workflow.api.WorkManagementContracts.TaskStatusRequest;
import com.nanobase.specai.workflow.application.WorkManagementService;
import com.nanobase.specai.workflow.application.DynamicWorkflowService;
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
@RequestMapping("/api/v1/tasks")
public class TaskController {
    private final WorkManagementService tasks;
    private final DynamicWorkflowService workflows;

    public TaskController(WorkManagementService tasks, DynamicWorkflowService workflows) {
        this.tasks = tasks;
        this.workflows = workflows;
    }

    @GetMapping
    List<TaskResponse> list(@RequestParam(required = false) UUID projectId,
                            @RequestParam(required = false) UUID statusConceptId,
                            @RequestParam(defaultValue = "false") boolean assignedToMe) {
        return tasks.list(projectId, statusConceptId, assignedToMe);
    }

    @GetMapping("/{id}")
    TaskResponse get(@PathVariable UUID id) {
        return tasks.getTask(id);
    }

    @PostMapping("/{id}/claim")
    TaskResponse claim(@PathVariable UUID id) {
        return tasks.claim(id);
    }

    @PostMapping("/{id}/assign")
    TaskResponse assign(@PathVariable UUID id,
                        @RequestBody AssignTaskRequest request) {
        return tasks.assign(id, request);
    }

    @PostMapping("/{id}/complete")
    TaskResponse complete(@PathVariable UUID id,
                          @Valid @RequestBody TaskStatusRequest request) {
        TaskResponse completed = tasks.changeStatus(id, request.targetStatusConceptId(),
            "complete");
        if (completed.workflowExecutionId() != null) {
            var output = JsonNodeFactory.instance.objectNode();
            output.put("taskId", completed.id().toString());
            output.put("statusConceptId", completed.statusConceptId().toString());
            workflows.resume(completed.workflowExecutionId(), output);
        }
        return completed;
    }

    @PostMapping("/{id}/block")
    TaskResponse block(@PathVariable UUID id,
                       @Valid @RequestBody TaskStatusRequest request) {
        return tasks.changeStatus(id, request.targetStatusConceptId(), "block");
    }

    @PostMapping("/{id}/escalate")
    Map<String, Object> escalate(@PathVariable UUID id,
                                 @Valid @RequestBody EscalateTaskRequest request) {
        return tasks.escalate(id, request);
    }

    @PostMapping("/{id}/comments")
    TaskResponse comment(@PathVariable UUID id,
                         @Valid @RequestBody AddCommentRequest request) {
        return tasks.comment(id, request);
    }
}
