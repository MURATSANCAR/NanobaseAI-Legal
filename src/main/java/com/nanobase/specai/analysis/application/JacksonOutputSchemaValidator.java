package com.nanobase.specai.analysis.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Validates the JSON-Schema vocabulary used by managed output contracts without coupling
 * extraction code to a sector DTO. Unsupported schema keywords fail closed.
 */
@Service
public class JacksonOutputSchemaValidator implements DynamicOutputSchemaValidator {
    private static final Set<String> SUPPORTED = Set.of(
        "$schema", "$id", "title", "description", "type", "required", "properties",
        "items", "minItems", "maxItems", "minLength", "maxLength", "enum", "const",
        "additionalProperties", "minimum", "maximum", "pattern", "x-grounding");

    @Override
    public List<String> validate(JsonNode schema, JsonNode value) {
        List<String> errors = new ArrayList<>();
        validateNode(schema, value, "$", errors);
        return List.copyOf(errors);
    }

    private void validateNode(JsonNode schema, JsonNode value, String path,
                              List<String> errors) {
        if (schema == null || !schema.isObject()) {
            errors.add(path + ": schema must be an object");
            return;
        }
        schema.fieldNames().forEachRemaining(keyword -> {
            if (!SUPPORTED.contains(keyword)) {
                errors.add(path + ": unsupported schema keyword " + keyword);
            }
        });
        if (!matchesType(schema.path("type"), value)) {
            errors.add(path + ": value does not match declared type");
            return;
        }
        if (schema.has("enum") && schema.path("enum").isArray()) {
            boolean matched = false;
            for (JsonNode allowed : schema.path("enum")) {
                matched |= allowed.equals(value);
            }
            if (!matched) {
                errors.add(path + ": value is not in enum");
            }
        }
        if (schema.has("const") && !schema.path("const").equals(value)) {
            errors.add(path + ": value does not equal const");
        }
        if (value != null && value.isObject()) {
            validateObject(schema, value, path, errors);
        } else if (value != null && value.isArray()) {
            validateArray(schema, value, path, errors);
        } else if (value != null && value.isTextual()) {
            int length = value.textValue().length();
            if (schema.path("minLength").isIntegralNumber()
                && length < schema.path("minLength").intValue()) {
                errors.add(path + ": string is shorter than minLength");
            }
            if (schema.path("maxLength").isIntegralNumber()
                && length > schema.path("maxLength").intValue()) {
                errors.add(path + ": string is longer than maxLength");
            }
            if (schema.path("pattern").isTextual()
                && !value.textValue().matches(schema.path("pattern").textValue())) {
                errors.add(path + ": string does not match schema pattern");
            }
        } else if (value != null && value.isNumber()) {
            if (schema.path("minimum").isNumber()
                && value.doubleValue() < schema.path("minimum").doubleValue()) {
                errors.add(path + ": number is below minimum");
            }
            if (schema.path("maximum").isNumber()
                && value.doubleValue() > schema.path("maximum").doubleValue()) {
                errors.add(path + ": number is above maximum");
            }
        }
    }

    private void validateObject(JsonNode schema, JsonNode value, String path,
                                List<String> errors) {
        Set<String> required = new HashSet<>();
        schema.path("required").forEach(item -> required.add(item.asText()));
        required.stream().filter(field -> !value.has(field))
            .forEach(field -> errors.add(path + ": required property is missing: " + field));
        JsonNode properties = schema.path("properties");
        Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (properties.has(field.getKey())) {
                validateNode(properties.get(field.getKey()), field.getValue(),
                    path + "." + field.getKey(), errors);
            } else if (schema.has("additionalProperties")
                && schema.path("additionalProperties").isBoolean()
                && !schema.path("additionalProperties").booleanValue()) {
                errors.add(path + ": additional property is not allowed: " + field.getKey());
            } else if (schema.path("additionalProperties").isObject()) {
                validateNode(schema.path("additionalProperties"), field.getValue(),
                    path + "." + field.getKey(), errors);
            }
        }
    }

    private void validateArray(JsonNode schema, JsonNode value, String path,
                               List<String> errors) {
        if (schema.path("minItems").isIntegralNumber()
            && value.size() < schema.path("minItems").intValue()) {
            errors.add(path + ": array has fewer than minItems");
        }
        if (schema.path("maxItems").isIntegralNumber()
            && value.size() > schema.path("maxItems").intValue()) {
            errors.add(path + ": array has more than maxItems");
        }
        if (schema.path("items").isObject()) {
            for (int index = 0; index < value.size(); index++) {
                validateNode(schema.path("items"), value.get(index),
                    path + "[" + index + "]", errors);
            }
        }
    }

    private boolean matchesType(JsonNode type, JsonNode value) {
        if (type == null || type.isMissingNode()) {
            return true;
        }
        if (type.isArray()) {
            for (JsonNode item : type) {
                if (matchesType(item, value)) {
                    return true;
                }
            }
            return false;
        }
        if (!type.isTextual()) {
            return false;
        }
        return switch (type.textValue()) {
            case "object" -> value != null && value.isObject();
            case "array" -> value != null && value.isArray();
            case "string" -> value != null && value.isTextual();
            case "number" -> value != null && value.isNumber();
            case "integer" -> value != null && value.isIntegralNumber();
            case "boolean" -> value != null && value.isBoolean();
            case "null" -> value == null || value.isNull();
            default -> false;
        };
    }
}
