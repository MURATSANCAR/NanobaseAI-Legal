package com.nanobase.specai.compliance.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.compliance.application.ComplianceModels.ComparisonContext;
import com.nanobase.specai.compliance.application.ComplianceModels.ComparisonResult;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BooleanExistenceComparisonStrategy implements ComparisonStrategy {
    private final ObjectMapper mapper;

    public BooleanExistenceComparisonStrategy(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean supports(ComparisonContext context) {
        return "boolean-existence".equals(context.providerCode());
    }

    @Override
    public ComparisonResult compare(ComparisonContext context) {
        boolean expected = context.requiredBoolean() == null || context.requiredBoolean();
        boolean satisfied = context.evidenceBoolean() != null
            && context.evidenceBoolean() == expected;
        ObjectNode explanation = mapper.createObjectNode();
        explanation.put("required", expected);
        if (context.evidenceBoolean() != null) {
            explanation.put("evidence", context.evidenceBoolean());
        }
        return new ComparisonResult(context.providerCode(),
            satisfied ? "SATISFIED" : "NOT_SATISFIED", true, explanation, List.of());
    }
}
