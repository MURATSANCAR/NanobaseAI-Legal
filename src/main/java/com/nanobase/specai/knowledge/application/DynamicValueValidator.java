package com.nanobase.specai.knowledge.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.knowledge.domain.KnowledgeModels.DynamicValue;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DynamicValueValidator {
    private static final Set<String> BUILT_IN_SHAPES = Set.of(
        "TEXT", "NUMBER", "RANGE", "BOOLEAN", "DATE", "DATETIME", "DURATION",
        "QUANTITY", "REFERENCE", "ENUM_CONCEPT", "JSON"
    );

    public DynamicValue normalize(DynamicValue value, JsonNode validationPolicy) {
        if (value == null || value.type() == null || value.type().isBlank()) {
            throw new IllegalArgumentException("Dynamic value type is required");
        }
        if (!BUILT_IN_SHAPES.contains(value.type())) {
            if ("REJECT".equals(validationPolicy.path("unsupportedValueTypeAction").asText())) {
                throw new IllegalArgumentException("Unsupported dynamic value type");
            }
            Map<String, Object> metadata = value.unsupportedMetadata() == null
                ? Map.of("originalType", value.type()) : value.unsupportedMetadata();
            return new DynamicValue(value.type(), value.textValue(), value.numericValue(),
                value.numericValueEnd(), value.booleanValue(), value.dateValue(),
                value.jsonValue(), value.unitConceptId(), metadata);
        }
        boolean present = switch (value.type()) {
            case "TEXT" -> value.textValue() != null;
            case "NUMBER", "QUANTITY", "DURATION" -> value.numericValue() != null;
            case "RANGE" -> value.numericValue() != null && value.numericValueEnd() != null;
            case "BOOLEAN" -> value.booleanValue() != null;
            case "DATE", "DATETIME" -> value.dateValue() != null;
            case "JSON", "REFERENCE", "ENUM_CONCEPT" -> value.jsonValue() != null;
            default -> true;
        };
        if (!present) {
            throw new IllegalArgumentException("Value does not match its dynamic type");
        }
        if ("RANGE".equals(value.type())
            && value.numericValue().compareTo(value.numericValueEnd()) > 0) {
            throw new IllegalArgumentException("Range start must not exceed range end");
        }
        return value;
    }
}
