package com.nanobase.specai.workflow.application;

import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowNodeExecutionContext;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowNodeExecutionResult;

public interface WorkflowNodeActionProvider {
    boolean supports(String providerCode);

    WorkflowNodeExecutionResult execute(WorkflowNodeExecutionContext context);
}
