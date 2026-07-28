package com.nanobase.specai.workflow.application;

import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowNodeExecutionContext;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowNodeExecutionResult;

public interface WorkflowNodeHandler {
    boolean supports(String nodeTypeConceptCode);

    WorkflowNodeExecutionResult execute(WorkflowNodeExecutionContext context);
}
