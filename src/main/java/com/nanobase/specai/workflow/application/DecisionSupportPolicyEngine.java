package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.workflow.application.WorkflowModels.DecisionSupportContext;
import com.nanobase.specai.workflow.application.WorkflowModels.DecisionSupportResult;

public interface DecisionSupportPolicyEngine {
    DecisionSupportResult evaluate(DecisionSupportContext context, JsonNode policy);
}
