package com.nanobase.specai.compliance.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.compliance.application.ComplianceModels.ComparisonContext;
import com.nanobase.specai.compliance.application.ComplianceModels.ComparisonResult;
import com.nanobase.specai.knowledge.application.UnitConversionService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NumericThresholdComparisonStrategy implements ComparisonStrategy {
    private final UnitConversionService units;
    private final ObjectMapper mapper;

    public NumericThresholdComparisonStrategy(UnitConversionService units,
                                              ObjectMapper mapper) {
        this.units = units;
        this.mapper = mapper;
    }

    @Override
    public boolean supports(ComparisonContext context) {
        return "numeric-threshold".equals(context.providerCode());
    }

    @Override
    public ComparisonResult compare(ComparisonContext context) {
        require(context.requiredValue(), "Required numeric value");
        require(context.evidenceValue(), "Evidence numeric value");
        BigDecimal evidence = context.evidenceValue();
        boolean compatible = context.requiredUnitConceptId() == null
            && context.evidenceUnitConceptId() == null;
        if (context.requiredUnitConceptId() != null && context.evidenceUnitConceptId() != null) {
            var conversion = units.convert(
                (java.util.UUID) context.metadata().get("organizationId"), evidence,
                context.evidenceUnitConceptId(), context.requiredUnitConceptId());
            compatible = conversion.compatible();
            if (compatible) {
                evidence = conversion.value();
            }
        }
        boolean satisfied = compatible && compare(evidence, context.requiredValue(),
            context.operator());
        ObjectNode explanation = mapper.createObjectNode();
        explanation.put("provider", context.providerCode());
        explanation.put("operator", context.operator());
        explanation.put("requiredValue", context.requiredValue());
        explanation.put("evidenceValue", evidence);
        explanation.put("unitCompatible", compatible);
        return new ComparisonResult(context.providerCode(),
            satisfied ? "SATISFIED" : "NOT_SATISFIED", true, explanation,
            compatible ? List.of() : List.of("UNIT_INCOMPATIBLE"));
    }

    private boolean compare(BigDecimal evidence, BigDecimal required, String operator) {
        int comparison = evidence.compareTo(required);
        return switch (operator == null ? "" : operator) {
            case "GREATER_THAN" -> comparison > 0;
            case "GREATER_THAN_OR_EQUAL" -> comparison >= 0;
            case "LESS_THAN" -> comparison < 0;
            case "LESS_THAN_OR_EQUAL" -> comparison <= 0;
            case "EQUAL" -> comparison == 0;
            default -> throw new IllegalArgumentException("Unsupported numeric operator");
        };
    }

    private void require(Object value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
    }
}
