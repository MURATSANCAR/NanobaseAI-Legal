package com.nanobase.specai.pilot.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ExplainableErrorRootCauseAnalyzer implements ErrorRootCauseAnalyzer {
    private static final Map<String, String> SIGNAL_CAUSES = Map.ofEntries(
        Map.entry("sourceMissing", "SOURCE_DATA"),
        Map.entry("documentCorrupt", "DOCUMENT_QUALITY"),
        Map.entry("securityScanFailure", "SECURITY_SCAN"),
        Map.entry("parserWarning", "PARSER"),
        Map.entry("lowOcrQuality", "OCR"),
        Map.entry("clauseBoundaryMismatch", "CLAUSE_SEGMENTATION"),
        Map.entry("parentClauseMissing", "CONTEXT_SELECTION"),
        Map.entry("ontologyMiss", "ONTOLOGY"),
        Map.entry("termMiss", "TERMINOLOGY"),
        Map.entry("policyMismatch", "POLICY"),
        Map.entry("promptRegression", "PROMPT"),
        Map.entry("modelRegression", "MODEL"),
        Map.entry("routingMismatch", "MODEL_ROUTING"),
        Map.entry("schemaFailure", "OUTPUT_SCHEMA"),
        Map.entry("ungrounded", "GROUNDING"),
        Map.entry("entityAmbiguous", "ENTITY_RESOLUTION"),
        Map.entry("retrievalMiss", "RETRIEVAL"),
        Map.entry("rerankingMiss", "RERANKING"),
        Map.entry("comparisonMismatch", "COMPARISON"),
        Map.entry("confidenceMiscalibrated", "CONFIDENCE"),
        Map.entry("authorizationDenied", "AUTHORIZATION"),
        Map.entry("frontendOnly", "FRONTEND"),
        Map.entry("latencyBudgetExceeded", "PERFORMANCE"),
        Map.entry("infrastructureFailure", "INFRASTRUCTURE"),
        Map.entry("labelDisagreement", "LABEL_QUALITY")
    );

    @Override
    public RootCauseAnalysisResult analyze(
        ErrorAnalysisContext context,
        ErrorAnalysisPolicyVersion policy
    ) {
        Map<String, Double> causeScores = new LinkedHashMap<>();
        List<RootCauseAnalysisResult.ContributingFactor> factors = new ArrayList<>();
        JsonNode signals = context.signals();
        if (signals != null && signals.isObject()) {
            SIGNAL_CAUSES.forEach((signal, cause) -> {
                JsonNode value = signals.get(signal);
                double strength = strength(value);
                if (strength <= 0) {
                    return;
                }
                double weight = policy.signalWeights().getOrDefault(signal, 1.0);
                double effect = clamp(strength * weight);
                causeScores.merge(cause, effect, Double::sum);
                factors.add(new RootCauseAnalysisResult.ContributingFactor(
                    signalToConcept(signal), effect,
                    "signal=" + signal + ", observed=" + value));
            });
        }
        applyFeedbackPrior(context.feedbackType(), causeScores, factors);
        if (causeScores.isEmpty()) {
            String fallback = "EXPECTED_BEHAVIOR".equals(context.feedbackClassification())
                ? "USER_EXPECTATION" : "SOURCE_DATA";
            return new RootCauseAnalysisResult(fallback, 0.25, List.of(),
                List.of("MANUAL_INVESTIGATION"),
                "No policy-supported signal was available; fallback is low confidence.",
                true);
        }
        var ranked = causeScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .toList();
        double total = ranked.stream().mapToDouble(Map.Entry::getValue).sum();
        Map.Entry<String, Double> primary = ranked.getFirst();
        double confidence = clamp(primary.getValue() / Math.max(total, 0.0001));
        factors.sort(Comparator.comparingDouble(
            RootCauseAnalysisResult.ContributingFactor::effect).reversed());
        List<String> areas = ranked.stream().limit(3)
            .map(entry -> investigationArea(entry.getKey())).toList();
        String explanation = "Policy " + policy.policyCode() + " v" + policy.version()
            + " selected " + primary.getKey() + " from " + factors.size()
            + " explicit signal(s); suggestion confidence=" + round(confidence)
            + ". Final triage remains a human decision.";
        return new RootCauseAnalysisResult(primary.getKey(), round(confidence),
            factors.stream().limit(8).toList(), areas, explanation, true);
    }

    private void applyFeedbackPrior(
        String feedbackType,
        Map<String, Double> scores,
        List<RootCauseAnalysisResult.ContributingFactor> factors
    ) {
        if (feedbackType == null) {
            return;
        }
        String cause = switch (feedbackType) {
            case "WRONG_GROUNDING", "WRONG_EVIDENCE" -> "GROUNDING";
            case "WRONG_ENTITY_MATCH" -> "ENTITY_RESOLUTION";
            case "WRONG_EXTRACTION" -> "PARSER";
            case "WRONG_COMPLIANCE" -> "COMPARISON";
            case "WRONG_RISK" -> "RISK_ANALYSIS";
            case "WRONG_CONFLICT" -> "CONFLICT_ANALYSIS";
            case "USABILITY", "DOCUMENT_RENDERING" -> "FRONTEND";
            case "PERFORMANCE" -> "PERFORMANCE";
            case "SECURITY" -> "SECURITY_SCAN";
            case "REPORTING" -> "REPORTING";
            case "WORKFLOW" -> "WORKFLOW";
            default -> null;
        };
        if (cause != null) {
            scores.merge(cause, 0.20, Double::sum);
            factors.add(new RootCauseAnalysisResult.ContributingFactor(
                "FEEDBACK_TYPE_PRIOR", 0.20, "feedbackType=" + feedbackType));
        }
    }

    private double strength(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return 0;
        }
        if (value.isBoolean()) {
            return value.booleanValue() ? 1 : 0;
        }
        if (value.isNumber()) {
            return clamp(value.doubleValue());
        }
        return value.asText().isBlank() ? 0 : 0.5;
    }

    private String signalToConcept(String signal) {
        return signal.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
    }

    private String investigationArea(String cause) {
        return switch (cause) {
            case "CONTEXT_SELECTION" -> "CONTEXT_POLICY";
            case "OCR" -> "OCR_REPROCESS";
            case "PARSER" -> "PARSER_CONFIGURATION";
            case "MODEL", "PROMPT", "MODEL_ROUTING" -> "OFFLINE_EVALUATION";
            case "GROUNDING", "RETRIEVAL", "RERANKING" -> "EVIDENCE_PIPELINE";
            default -> cause;
        };
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
