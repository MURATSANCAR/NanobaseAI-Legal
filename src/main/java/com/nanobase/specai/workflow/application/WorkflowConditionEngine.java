package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.workflow.application.WorkflowModels.ConditionEvaluationResult;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowConditionContext;

public interface WorkflowConditionEngine {
    ConditionEvaluationResult evaluate(WorkflowConditionContext context, JsonNode expression);
}
