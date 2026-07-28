package com.nanobase.specai.workflow.application;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WorkflowNodeHandlerRegistry {
    private final List<WorkflowNodeHandler> handlers;

    public WorkflowNodeHandlerRegistry(List<WorkflowNodeHandler> handlers) {
        this.handlers = List.copyOf(handlers);
    }

    public WorkflowNodeHandler require(String nodeTypeConceptCode) {
        return handlers.stream().filter(handler -> handler.supports(nodeTypeConceptCode))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No workflow node provider for concept " + nodeTypeConceptCode));
    }

    public boolean supports(String nodeTypeConceptCode) {
        return handlers.stream().anyMatch(handler -> handler.supports(nodeTypeConceptCode));
    }
}
