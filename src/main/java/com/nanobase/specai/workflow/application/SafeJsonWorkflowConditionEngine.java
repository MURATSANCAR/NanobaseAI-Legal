package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.workflow.application.WorkflowModels.ConditionEvaluationResult;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowConditionContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SafeJsonWorkflowConditionEngine implements WorkflowConditionEngine {
    private static final Pattern SAFE_FIELD =
        Pattern.compile("[A-Za-z][A-Za-z0-9_-]*(\\.[A-Za-z0-9_-]+)*");
    private static final int MAX_DEPTH = 32;
    private static final int MAX_NODES = 500;

    private final ObjectMapper mapper;

    public SafeJsonWorkflowConditionEngine(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ConditionEvaluationResult evaluate(WorkflowConditionContext context,
                                               JsonNode expression) {
        if (expression == null || expression.isNull()
            || (expression.isObject() && expression.size() == 0)) {
            return new ConditionEvaluationResult(true, List.of());
        }
        List<String> diagnostics = new ArrayList<>();
        Counter counter = new Counter();
        boolean matched = evaluateNode(context.variables(), expression, 0, counter, diagnostics);
        return new ConditionEvaluationResult(matched, diagnostics);
    }

    private boolean evaluateNode(Map<String, Object> variables, JsonNode node, int depth,
                                 Counter counter, List<String> diagnostics) {
        if (depth > MAX_DEPTH || ++counter.value > MAX_NODES) {
            throw new IllegalArgumentException("Condition expression exceeds safety limits");
        }
        int operators = (node.has("all") ? 1 : 0) + (node.has("any") ? 1 : 0)
            + (node.has("not") ? 1 : 0) + (node.has("field") ? 1 : 0);
        if (!node.isObject() || operators != 1) {
            throw new IllegalArgumentException("Condition node must contain exactly one operation");
        }
        if (node.has("all")) {
            requireArray(node.get("all"), "all");
            for (JsonNode child : node.get("all")) {
                if (!evaluateNode(variables, child, depth + 1, counter, diagnostics)) {
                    return false;
                }
            }
            return true;
        }
        if (node.has("any")) {
            requireArray(node.get("any"), "any");
            for (JsonNode child : node.get("any")) {
                if (evaluateNode(variables, child, depth + 1, counter, diagnostics)) {
                    return true;
                }
            }
            return false;
        }
        if (node.has("not")) {
            return !evaluateNode(variables, node.get("not"), depth + 1, counter, diagnostics);
        }
        return evaluateLeaf(variables, node, diagnostics);
    }

    private boolean evaluateLeaf(Map<String, Object> variables, JsonNode node,
                                 List<String> diagnostics) {
        String field = requiredText(node, "field");
        if (!SAFE_FIELD.matcher(field).matches()) {
            throw new IllegalArgumentException("Unsafe condition field path: " + field);
        }
        String operator = requiredText(node, "operator");
        Object actual = resolve(variables, field);
        JsonNode expected = node.get("value");
        boolean result = switch (operator) {
            case "EQUAL" -> equalsValue(actual, expected);
            case "NOT_EQUAL" -> !equalsValue(actual, expected);
            case "GREATER_THAN" -> compare(actual, expected) > 0;
            case "GREATER_THAN_OR_EQUAL" -> compare(actual, expected) >= 0;
            case "LESS_THAN" -> compare(actual, expected) < 0;
            case "LESS_THAN_OR_EQUAL" -> compare(actual, expected) <= 0;
            case "IN" -> in(actual, expected);
            case "NOT_IN" -> !in(actual, expected);
            case "CONTAINS" -> contains(actual, expected);
            case "EXISTS" -> actual != null;
            case "IS_EMPTY" -> empty(actual);
            default -> throw new IllegalArgumentException(
                "Unsupported safe condition operator: " + operator);
        };
        if (!result) {
            diagnostics.add(field + " did not satisfy " + operator);
        }
        return result;
    }

    private Object resolve(Object current, String field) {
        for (String segment : field.split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(segment);
            } else if (current instanceof JsonNode json) {
                JsonNode value = json.get(segment);
                current = value == null || value.isNull() ? null : value;
            } else {
                return null;
            }
        }
        return current;
    }

    private boolean equalsValue(Object actual, JsonNode expected) {
        if (actual == null) {
            return expected == null || expected.isNull();
        }
        return mapper.valueToTree(actual).equals(expected);
    }

    private int compare(Object actual, JsonNode expected) {
        if (actual == null || expected == null || expected.isNull()) {
            return -1;
        }
        if (actual instanceof JsonNode json) {
            actual = json.isNumber() ? json.decimalValue() : json.asText();
        }
        if (actual instanceof Number number && expected.isNumber()) {
            return new BigDecimal(number.toString()).compareTo(expected.decimalValue());
        }
        return String.valueOf(actual).compareTo(expected.asText());
    }

    private boolean in(Object actual, JsonNode expected) {
        requireArray(expected, "value");
        for (JsonNode candidate : expected) {
            if (equalsValue(actual, candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(Object actual, JsonNode expected) {
        if (actual instanceof Collection<?> collection) {
            return collection.stream().anyMatch(item -> equalsValue(item, expected));
        }
        if (actual instanceof JsonNode json && json.isArray()) {
            for (JsonNode item : json) {
                if (Objects.equals(item, expected)) {
                    return true;
                }
            }
            return false;
        }
        return actual != null && expected != null
            && String.valueOf(actual).contains(expected.asText());
    }

    private boolean empty(Object actual) {
        return actual == null
            || actual instanceof String value && value.isBlank()
            || actual instanceof Collection<?> value && value.isEmpty()
            || actual instanceof Map<?, ?> value && value.isEmpty()
            || actual instanceof JsonNode value && value.isContainerNode() && value.size() == 0;
    }

    private static String requiredText(JsonNode node, String field) {
        if (!node.hasNonNull(field) || !node.get(field).isTextual()
            || node.get(field).asText().isBlank()) {
            throw new IllegalArgumentException("Condition " + field + " is required");
        }
        return node.get(field).asText();
    }

    private static void requireArray(JsonNode value, String field) {
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException("Condition " + field + " must be an array");
        }
    }

    private static final class Counter {
        private int value;
    }
}
