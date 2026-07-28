package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.workflow.application.WorkflowModels.PolicyEvaluationResult;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowConditionContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ConfigurablePolicyGate {
    private final WorkflowConditionEngine conditions;

    public ConfigurablePolicyGate(WorkflowConditionEngine conditions) {
        this.conditions = conditions;
    }

    public PolicyEvaluationResult evaluate(Map<String, Object> verifiedFacts,
                                           JsonNode configuration) {
        List<String> failed = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (JsonNode rule : configuration.path("rules")) {
            boolean matched = conditions.evaluate(new WorkflowConditionContext(verifiedFacts),
                rule.path("condition")).matched();
            if (!matched) {
                String code = rule.path("code").asText("UNNAMED_RULE");
                if ("WARNING".equals(rule.path("severity").asText("BLOCKING"))) {
                    warnings.add(code);
                } else {
                    failed.add(code);
                }
            }
        }
        return new PolicyEvaluationResult(failed.isEmpty(), failed, warnings, verifiedFacts);
    }
}
