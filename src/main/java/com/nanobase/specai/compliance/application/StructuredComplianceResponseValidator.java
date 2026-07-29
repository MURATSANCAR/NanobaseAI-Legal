package com.nanobase.specai.compliance.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Validates structured compliance JSON against decision semantics and evidence grounding.
 *
 * <p>Missing information alone must not become {@code NON_COMPLIANT}. Imaginary evidence IDs
 * are rejected as {@code LLM_INVALID_RESPONSE}.
 */
public final class StructuredComplianceResponseValidator {

    public record ValidationResult(boolean valid, SemanticEvaluationFailureCode failureCode,
                                   String message) {
        static ValidationResult ok() {
            return new ValidationResult(true, null, null);
        }

        static ValidationResult fail(SemanticEvaluationFailureCode code, String message) {
            return new ValidationResult(false, code, message);
        }
    }

    public ValidationResult validate(JsonNode output, Set<String> allowedEvidenceIds,
                                     Set<String> allowedDecisions) {
        if (output == null || output.isNull() || !output.isObject()) {
            return ValidationResult.fail(SemanticEvaluationFailureCode.LLM_INVALID_RESPONSE,
                "Compliance output is missing or not an object");
        }
        String decision = text(output, "recommendedDecisionConcept");
        if (decision == null || decision.isBlank()) {
            // Prefer newer schema field when present.
            decision = text(output, "decision");
        }
        if (decision == null || decision.isBlank()) {
            return ValidationResult.fail(SemanticEvaluationFailureCode.LLM_INVALID_RESPONSE,
                "Compliance output missing decision");
        }
        if (allowedDecisions != null && !allowedDecisions.isEmpty()
            && !allowedDecisions.contains(decision)) {
            return ValidationResult.fail(SemanticEvaluationFailureCode.LLM_INVALID_RESPONSE,
                "Unsupported decision concept: " + decision);
        }

        Set<String> supporting = collectIds(output, "supportingEvidenceIds");
        Set<String> contradicting = collectIds(output, "contradictingEvidenceIds");
        for (JsonNode evaluation : output.path("conditionEvaluations")) {
            supporting.addAll(collectIds(evaluation, "supportingEvidenceIds"));
            contradicting.addAll(collectIds(evaluation, "contradictingEvidenceIds"));
        }
        Set<String> used = new LinkedHashSet<>();
        used.addAll(supporting);
        used.addAll(contradicting);
        if (allowedEvidenceIds != null && !allowedEvidenceIds.containsAll(used)) {
            return ValidationResult.fail(SemanticEvaluationFailureCode.LLM_INVALID_RESPONSE,
                "Output references evidence IDs absent from candidates");
        }

        boolean explicitContradiction = output.path("explicitContradiction").asBoolean(false)
            || !contradicting.isEmpty();
        boolean closedWorldApplied = output.path("closedWorldApplied").asBoolean(false);

        return switch (decision) {
            case "COMPLIANT", "PARTIALLY_COMPLIANT" -> {
                if (supporting.isEmpty()) {
                    yield ValidationResult.fail(
                        SemanticEvaluationFailureCode.LLM_INVALID_RESPONSE,
                        "COMPLIANT requires supportingEvidenceIds");
                }
                yield ValidationResult.ok();
            }
            case "NON_COMPLIANT" -> {
                // Prefer explicitContradiction / closedWorldApplied / contradicting IDs.
                // During schema rollout, do not reject legacy payloads that omit new flags
                // when the model still returned a grounded NON_COMPLIANT with evidence IDs.
                if (!explicitContradiction && !closedWorldApplied && contradicting.isEmpty()
                    && supporting.isEmpty()
                    && allowedEvidenceIds != null && !allowedEvidenceIds.isEmpty()) {
                    yield ValidationResult.fail(
                        SemanticEvaluationFailureCode.LLM_INVALID_RESPONSE,
                        "NON_COMPLIANT requires contradiction evidence or closedWorldApplied");
                }
                yield ValidationResult.ok();
            }
            case "INSUFFICIENT_INFORMATION" -> ValidationResult.ok();
            default -> ValidationResult.ok();
        };
    }

    private static Set<String> collectIds(JsonNode node, String field) {
        Set<String> ids = new LinkedHashSet<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return ids;
        }
        for (JsonNode item : node.path(field)) {
            if (item != null && item.isTextual() && !item.asText().isBlank()) {
                ids.add(item.asText());
            }
        }
        return ids;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value == null || value.isNull() || !value.isTextual()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
