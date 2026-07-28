package com.nanobase.specai.analysis.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.analysis.application.AnalysisModels.ClauseAnalysisContext;
import com.nanobase.specai.analysis.application.AnalysisModels.ExtractionStrategy;
import com.nanobase.specai.analysis.domain.AnalysisProfile;
import java.util.Iterator;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PolicyExtractionStrategyResolver implements ExtractionStrategyResolver {
    private final AnalysisCatalogPort catalog;

    public PolicyExtractionStrategyResolver(AnalysisCatalogPort catalog) {
        this.catalog = catalog;
    }

    @Override
    public ExtractionStrategy resolve(ClauseAnalysisContext context, AnalysisProfile profile) {
        JsonNode root = catalog.policy(profile.organizationId(),
            profile.policyVersionId()).configuration();
        JsonNode strategies = root.path("strategies");
        if (!strategies.isArray() || strategies.isEmpty()) {
            throw new IllegalStateException("Extraction policy has no strategies");
        }
        for (JsonNode strategy : strategies) {
            if (matches(strategy.path("when"), context.features())) {
                return new ExtractionStrategy(
                    requiredText(strategy, "code"),
                    requiredText(strategy, "modelProfile"),
                    profile.promptPackageVersionId(),
                    requiredBoolean(strategy, "includeTables"),
                    requiredInteger(strategy, "maximumOutputTokens"),
                    requiredText(strategy, "validationPolicy"),
                    requiredText(strategy, "retryPolicy"),
                    strategy.path("secondModelValidation").asBoolean(false),
                    strategy.path("outputConfiguration")
                );
            }
        }
        throw new IllegalStateException("No extraction strategy matches the clause context");
    }

    private boolean matches(JsonNode predicates, Map<String, Object> features) {
        if (predicates == null || predicates.isMissingNode() || predicates.isEmpty()) {
            return true;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = predicates.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> predicate = fields.next();
            Object actual = features.get(predicate.getKey());
            if (actual == null || !compare(actual, predicate.getValue())) {
                return false;
            }
        }
        return true;
    }

    private boolean compare(Object actual, JsonNode predicate) {
        if (!predicate.isObject()) {
            return predicate.asText().equals(String.valueOf(actual));
        }
        if (predicate.has("equals")
            && !predicate.get("equals").asText().equals(String.valueOf(actual))) {
            return false;
        }
        if (actual instanceof Number number) {
            if (predicate.has("minimum")
                && number.doubleValue() < predicate.get("minimum").doubleValue()) {
                return false;
            }
            if (predicate.has("maximum")
                && number.doubleValue() > predicate.get("maximum").doubleValue()) {
                return false;
            }
        }
        return true;
    }

    private String requiredText(JsonNode node, String field) {
        if (!node.path(field).isTextual() || node.path(field).textValue().isBlank()) {
            throw new IllegalStateException("Strategy field is missing: " + field);
        }
        return node.path(field).textValue();
    }

    private int requiredInteger(JsonNode node, String field) {
        if (!node.path(field).isIntegralNumber()) {
            throw new IllegalStateException("Strategy field is missing: " + field);
        }
        return node.path(field).intValue();
    }

    private boolean requiredBoolean(JsonNode node, String field) {
        if (!node.path(field).isBoolean()) {
            throw new IllegalStateException("Strategy field is missing: " + field);
        }
        return node.path(field).booleanValue();
    }
}
