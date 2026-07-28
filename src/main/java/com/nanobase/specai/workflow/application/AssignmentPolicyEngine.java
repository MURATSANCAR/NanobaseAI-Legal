package com.nanobase.specai.workflow.application;

import com.nanobase.specai.workflow.application.WorkflowModels.AssignmentContext;
import com.nanobase.specai.workflow.application.WorkflowModels.AssignmentPolicyVersion;
import com.nanobase.specai.workflow.application.WorkflowModels.AssignmentResult;

public interface AssignmentPolicyEngine {
    AssignmentResult resolve(AssignmentContext context, AssignmentPolicyVersion policy);
}
