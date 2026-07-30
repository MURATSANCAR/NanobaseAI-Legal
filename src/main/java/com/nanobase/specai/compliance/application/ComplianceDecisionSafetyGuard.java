package com.nanobase.specai.compliance.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic decision safety for production compliance evaluations.
 *
 * <p>{@code NON_COMPLIANT} is accepted only when {@code explicitContradiction=true} or
 * {@code closedWorldApplied=true}. Otherwise the decision is remapped to
 * {@code INSUFFICIENT_INFORMATION}. Numeric threshold requirements without a comparable
 * evidence value cannot become {@code NON_COMPLIANT} via model claim alone.
 */
public final class ComplianceDecisionSafetyGuard {
    private static final Pattern NUMERIC_THRESHOLD = Pattern.compile(
        "(?i)(?:en\\s+az|minimum|min\\.?|at\\s+least|>=|≥|>|en\\s+fazla|maximum|max\\.?|"
            + "at\\s+most|<=|≤|<)\\s*"
            + "(\\d+(?:[.,]\\d+)?)\\s*"
            + "(km|m|%|yüzde|percent|saat|gün|dakika|dk|sn|saniye|adet|tb|gb|mb|"
            + "rto|rpo|sla)?");
    private static final Pattern NUMERIC_DIMENSION = Pattern.compile(
        "(?i)\\b(mesafe|distance|süre|duration|yüzde|percent|kapasite|capacity|"
            + "adet|count|rto|rpo|sla|geçerlilik|validity|threshold|eşik)\\b");
    private static final Pattern ANY_NUMBER = Pattern.compile(
        "(\\d+(?:[.,]\\d+)?)\\s*(km|m|%|yüzde|percent|saat|gün|dakika|dk|sn|saniye|"
            + "adet|tb|gb|mb|rto|rpo|sla)?",
        Pattern.CASE_INSENSITIVE);

    private final ObjectMapper mapper;

    public ComplianceDecisionSafetyGuard(ObjectMapper mapper) {
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    public ObjectNode normalize(JsonNode output, String requirementText,
                                List<Map<String, Object>> evidence) {
        ObjectNode result = output == null || !output.isObject()
            ? mapper.createObjectNode()
            : ((ObjectNode) output).deepCopy();

        String decision = decisionOf(result);
        boolean explicitContradiction = bool(result, "explicitContradiction");
        boolean closedWorldApplied = bool(result, "closedWorldApplied");

        NumericRequirementSignal numeric = detectNumeric(requirementText);
        boolean hasComparableNumericEvidence = hasComparableNumericEvidence(evidence, numeric);

        if (numeric.present() && !hasComparableNumericEvidence && explicitContradiction) {
            // Model may not invent a numeric contradiction when evidence has no value.
            explicitContradiction = false;
            result.put("explicitContradiction", false);
            result.put("decisionSafetyOverride", "NUMERIC_CONTRADICTION_WITHOUT_VALUE");
        }

        if ("NON_COMPLIANT".equals(decision)
            && !explicitContradiction
            && !closedWorldApplied) {
            result.put("recommendedDecisionConcept", "INSUFFICIENT_INFORMATION");
            result.put("decision", "INSUFFICIENT_INFORMATION");
            ensureMissingElements(result, numeric.present()
                ? "Required numeric threshold value not present in evidence"
                : "Required evidence element not present in candidates");
            if (!result.has("decisionSafetyOverride")
                || result.path("decisionSafetyOverride").asText("").isBlank()) {
                result.put("decisionSafetyOverride", "NON_COMPLIANT_WITHOUT_EXPLICIT_GROUNDING");
            }
            decision = "INSUFFICIENT_INFORMATION";
        }

        if ("INSUFFICIENT_INFORMATION".equals(decision)) {
            ensureMissingElements(result, numeric.present()
                ? "Required numeric threshold value not present in evidence"
                : "Required evidence element not present in candidates");
        }

        if ("COMPLIANT".equals(decision) || "PARTIALLY_COMPLIANT".equals(decision)) {
            // Leave supportingEvidenceIds validation to StructuredComplianceResponseValidator.
            result.put("explicitContradiction", false);
        }

        return result;
    }

    /**
     * Pure decision table used by regression tests and documentation.
     */
    public static String decide(boolean explicitContradiction, boolean closedWorldApplied,
                                boolean missingElements) {
        if (explicitContradiction || (closedWorldApplied && missingElements)) {
            return "NON_COMPLIANT";
        }
        if (missingElements) {
            return "INSUFFICIENT_INFORMATION";
        }
        return "COMPLIANT";
    }

    static NumericRequirementSignal detectNumeric(String requirementText) {
        if (requirementText == null || requirementText.isBlank()) {
            return NumericRequirementSignal.absent();
        }
        Matcher threshold = NUMERIC_THRESHOLD.matcher(requirementText);
        if (threshold.find()) {
            return new NumericRequirementSignal(true, parseDecimal(threshold.group(1)),
                normalizeUnit(threshold.group(2)));
        }
        if (NUMERIC_DIMENSION.matcher(requirementText).find()
            && ANY_NUMBER.matcher(requirementText).find()) {
            Matcher number = ANY_NUMBER.matcher(requirementText);
            if (number.find()) {
                return new NumericRequirementSignal(true, parseDecimal(number.group(1)),
                    normalizeUnit(number.group(2)));
            }
        }
        return NumericRequirementSignal.absent();
    }

    static boolean hasComparableNumericEvidence(List<Map<String, Object>> evidence,
                                                 NumericRequirementSignal numeric) {
        if (!numeric.present() || evidence == null || evidence.isEmpty()) {
            return false;
        }
        for (Map<String, Object> item : evidence) {
            if (item == null) {
                continue;
            }
            Object numericValue = firstNonNull(item.get("numericValue"), item.get("numeric_value"));
            if (numericValue instanceof Number || numericValue instanceof BigDecimal
                || numericValue instanceof String text && looksNumeric(text)) {
                return true;
            }
            Object text = firstNonNull(item.get("text"), item.get("claimText"),
                item.get("claim_text"), item.get("snippet"));
            if (text != null && ANY_NUMBER.matcher(String.valueOf(text)).find()) {
                return true;
            }
        }
        return false;
    }

    private void ensureMissingElements(ObjectNode result, String fallback) {
        ArrayNode missing = ensureArray(result, "missingRequirementElements");
        ArrayNode legacy = ensureArray(result, "missingInformation");
        if (missing.isEmpty() && legacy.isEmpty()) {
            missing.add(fallback);
            legacy.add(fallback);
            return;
        }
        if (missing.isEmpty()) {
            for (JsonNode item : legacy) {
                if (item != null && item.isTextual() && !item.asText().isBlank()) {
                    missing.add(item.asText());
                }
            }
        }
        if (legacy.isEmpty()) {
            for (JsonNode item : missing) {
                if (item != null && item.isTextual() && !item.asText().isBlank()) {
                    legacy.add(item.asText());
                }
            }
        }
    }

    private ArrayNode ensureArray(ObjectNode node, String field) {
        JsonNode existing = node.get(field);
        if (existing != null && existing.isArray()) {
            return (ArrayNode) existing;
        }
        ArrayNode created = mapper.createArrayNode();
        node.set(field, created);
        return created;
    }

    private static String decisionOf(JsonNode node) {
        String decision = text(node, "recommendedDecisionConcept");
        if (decision == null) {
            decision = text(node, "decision");
        }
        return decision == null ? "" : decision.trim();
    }

    private static boolean bool(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value != null && value.isBoolean() && value.asBoolean();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value == null || !value.isTextual()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim().replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalizeUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            return null;
        }
        return unit.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean looksNumeric(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        try {
            new BigDecimal(text.trim().replace(',', '.'));
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    record NumericRequirementSignal(boolean present, BigDecimal threshold, String unit) {
        static NumericRequirementSignal absent() {
            return new NumericRequirementSignal(false, null, null);
        }
    }
}
