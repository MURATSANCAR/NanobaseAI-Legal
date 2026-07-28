package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowNodeExecutionContext;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowNodeExecutionResult;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredAutomaticNodeHandler implements WorkflowNodeHandler {
    private final List<WorkflowNodeActionProvider> actionProviders;

    public ConfiguredAutomaticNodeHandler(List<WorkflowNodeActionProvider> actionProviders) {
        this.actionProviders = List.copyOf(actionProviders);
    }

    @Override
    public boolean supports(String nodeTypeConceptCode) {
        return nodeTypeConceptCode != null && !nodeTypeConceptCode.isBlank();
    }

    @Override
    public WorkflowNodeExecutionResult execute(WorkflowNodeExecutionContext context) {
        String providerCode = context.configuration().path("actionProvider").asText();
        if (!providerCode.isBlank()) {
            return actionProviders.stream().filter(provider -> provider.supports(providerCode))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "No workflow action provider for " + providerCode))
                .execute(context);
        }
        String behavior = context.configuration().path("executionBehavior").asText("COMPLETE");
        if ("WAIT".equals(behavior)) {
            return new WorkflowNodeExecutionResult(false, true,
                JsonNodeFactory.instance.objectNode(), List.of());
        }
        return new WorkflowNodeExecutionResult(true, false,
            context.input() == null ? JsonNodeFactory.instance.objectNode() : context.input(),
            List.of());
    }
}
