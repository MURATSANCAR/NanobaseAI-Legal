package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.workflow.application.WorkflowModels.ApprovalPolicyContext;
import com.nanobase.specai.workflow.application.WorkflowModels.ApprovalPolicyResult;

public interface ApprovalPolicyEngine {
    boolean supports(String approvalModeConceptCode);

    ApprovalPolicyResult evaluate(ApprovalPolicyContext context, JsonNode configuration);
}
