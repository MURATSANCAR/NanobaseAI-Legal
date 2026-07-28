package com.nanobase.specai.risk.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.risk.application.RiskModels.ConflictComparisonContext;
import com.nanobase.specai.risk.application.RiskModels.ConflictComparisonResult;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class StructuredValueConflictStrategy implements ConflictComparisonStrategy {
    @Override
    public boolean supports(ConflictComparisonContext context) {
        JsonNode left = context.candidate().left().attributes();
        JsonNode right = context.candidate().right().attributes();
        for (JsonNode rule : context.strategyConfiguration().path("structuredRules")) {
            String path = rule.path("path").asText();
            if (!path.isBlank() && left.at(path).isValueNode() && right.at(path).isValueNode()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ConflictComparisonResult compare(ConflictComparisonContext context) {
        JsonNode left = context.candidate().left().attributes();
        JsonNode right = context.candidate().right().attributes();
        for (JsonNode rule : context.strategyConfiguration().path("structuredRules")) {
            String path = rule.path("path").asText();
            JsonNode leftValue = left.at(path);
            JsonNode rightValue = right.at(path);
            if (!leftValue.isValueNode() || !rightValue.isValueNode()
                || equivalent(leftValue, rightValue,
                    rule.path("tolerance").asDouble(0))) {
                continue;
            }
            ObjectNode authority = authorityAssessment(context.authorityConfiguration());
            boolean manual = authority.path("preferredSourceId").isNull();
            return new ConflictComparisonResult(true,
                rule.path("conflictConceptCode").asText(),
                rule.path("strategyCode").asText("STRUCTURED_VALUE"),
                rule.path("description").asText("Structured source values differ."),
                context.candidate().retrievalScore(),
                interpretation(path, leftValue), interpretation(path, rightValue),
                authority, manual,
                List.of(context.candidate().left().id(), context.candidate().right().id()));
        }
        return new ConflictComparisonResult(false, null, "STRUCTURED_VALUE",
            "Configured structured values do not contradict.", 1,
            JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.objectNode(),
            authorityAssessment(context.authorityConfiguration()), false, List.of());
    }

    private boolean equivalent(JsonNode left, JsonNode right, double tolerance) {
        if (left.isNumber() && right.isNumber()) {
            return Math.abs(left.asDouble() - right.asDouble()) <= tolerance;
        }
        return Objects.equals(left.asText(), right.asText());
    }

    private ObjectNode interpretation(String path, JsonNode value) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("path", path);
        node.set("value", value);
        return node;
    }

    private ObjectNode authorityAssessment(JsonNode configuration) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.putNull("preferredSourceId");
        node.put("reason", configuration.path("onUnknown").asText("MANUAL_REVIEW"));
        return node;
    }
}
