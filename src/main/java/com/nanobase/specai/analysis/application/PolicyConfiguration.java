package com.nanobase.specai.analysis.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PolicyConfiguration {
    private final JsonNode root;

    public PolicyConfiguration(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Policy configuration must be a JSON object");
        }
        this.root = root;
    }

    public JsonNode root() {
        return root;
    }

    public double requiredNumber(String path) {
        JsonNode node = at(path);
        if (!node.isNumber()) {
            throw new IllegalStateException("Required numeric policy value is missing: " + path);
        }
        return node.doubleValue();
    }

    public int requiredInteger(String path) {
        JsonNode node = at(path);
        if (!node.isIntegralNumber()) {
            throw new IllegalStateException("Required integer policy value is missing: " + path);
        }
        return node.intValue();
    }

    public String requiredText(String path) {
        JsonNode node = at(path);
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw new IllegalStateException("Required text policy value is missing: " + path);
        }
        return node.textValue();
    }

    public boolean booleanValue(String path, boolean fallback) {
        JsonNode node = at(path);
        return node.isBoolean() ? node.booleanValue() : fallback;
    }

    public Map<String, Double> requiredWeights(String path) {
        JsonNode node = at(path);
        if (!node.isObject() || node.isEmpty()) {
            throw new IllegalStateException("Required policy weights are missing: " + path);
        }
        Map<String, Double> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!field.getValue().isNumber()) {
                throw new IllegalStateException("Policy weight must be numeric: " + field.getKey());
            }
            result.put(field.getKey(), field.getValue().doubleValue());
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private JsonNode at(String path) {
        String pointer = path.startsWith("/") ? path : "/" + path.replace(".", "/");
        return root.at(pointer);
    }
}
