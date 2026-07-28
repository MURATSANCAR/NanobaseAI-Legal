package com.nanobase.specai.compliance.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.compliance.application.ComplianceModels.ComparisonContext;
import com.nanobase.specai.compliance.application.ComplianceModels.ComparisonResult;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NumericRangeComparisonStrategy implements ComparisonStrategy {
    private final ObjectMapper mapper;

    public NumericRangeComparisonStrategy(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean supports(ComparisonContext context) {
        return "numeric-range".equals(context.providerCode());
    }

    @Override
    public ComparisonResult compare(ComparisonContext context) {
        if (context.requiredValue() == null || context.requiredValueEnd() == null
            || context.evidenceValue() == null || context.evidenceValueEnd() == null) {
            throw new IllegalArgumentException("Both numeric ranges are required");
        }
        boolean contained = context.evidenceValue().compareTo(context.requiredValue()) <= 0
            && context.evidenceValueEnd().compareTo(context.requiredValueEnd()) >= 0;
        ObjectNode explanation = mapper.createObjectNode();
        explanation.put("requiredStart", context.requiredValue());
        explanation.put("requiredEnd", context.requiredValueEnd());
        explanation.put("evidenceStart", context.evidenceValue());
        explanation.put("evidenceEnd", context.evidenceValueEnd());
        return new ComparisonResult(context.providerCode(),
            contained ? "SATISFIED" : "NOT_SATISFIED", true, explanation, List.of());
    }
}
