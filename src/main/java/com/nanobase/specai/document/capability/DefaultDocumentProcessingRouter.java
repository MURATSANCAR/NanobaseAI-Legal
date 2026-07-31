package com.nanobase.specai.document.capability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Policy-driven router. Uses profile concept codes and policy configuration maps —
 * no document-name or institution-specific branches.
 */
@Component
public class DefaultDocumentProcessingRouter implements DocumentProcessingRouter {

    @Override
    public DocumentProcessingPlan resolve(
        DocumentCapabilityProfile profile,
        DocumentProcessingPolicyVersion policy
    ) {
        Map<String, Object> cfg = policy == null || policy.configuration() == null
            ? Map.of() : policy.configuration();
        String contentMode = safe(profile.contentModeConceptCode());
        String ocrNeed = safe(profile.ocrNeedConceptCode());
        String format = safe(profile.formatConceptCode());

        String parser = firstNonBlank(
            profile.recommendedParserProfileCode(),
            stringAt(cfg, "defaultParser", "DOCLING_AUTO"));
        String ocr = firstNonBlank(
            profile.recommendedOcrProfileCode(),
            ocrProviderFor(ocrNeed));
        String layout = stringAt(cfg, "layoutProvider",
            "DOCX".equals(format) ? "DOCX_STRUCTURE" : "LAYOUT_BLOCKS");
        String table = stringAt(cfg, "tableProvider", "CANONICAL_TABLE");
        List<String> clauseChain = listAt(cfg, "clauseProviderChain", List.of(
            "DOCLING_STRUCTURE", "TEXT_HIERARCHY", "OBLIGATION_AWARE_FALLBACK"));
        String reqProfile = firstNonBlank(
            profile.recommendedExtractionProfileCode(),
            stringAt(cfg, "requirementProfile", "BALANCED"));
        String knowledgeProfile = "TABLE_DOMINANT".equals(contentMode)
            || contentMode.contains("CERTIFICATE")
            || contentMode.contains("DATASHEET")
            ? stringAt(cfg, "knowledgeProfile", "KNOWLEDGE_EVIDENCE")
            : stringAt(cfg, "knowledgeProfile", "PURPOSE_AWARE");

        Map<String, Object> budget = new LinkedHashMap<>();
        budget.put("maxPages", profile.pageCount());
        budget.put("estimatedTokens", profile.estimatedTokenCount());
        budget.put("layoutComplexity", profile.layoutComplexityConceptCode());
        Object policyBudget = cfg.get("resourceBudget");
        if (policyBudget instanceof Map<?, ?> map) {
            map.forEach((k, v) -> budget.put(String.valueOf(k), v));
        }

        Map<String, Object> retry = mapAt(cfg, "retryPolicy", Map.of(
            "maxPageRetries", 2,
            "allowAlternativeOcr", "REQUIRED".equals(ocrNeed) || "RECOMMENDED".equals(ocrNeed),
            "preferPageScopedRetry", true
        ));
        Map<String, Object> review = mapAt(cfg, "manualReviewPolicy", Map.of(
            "onOcrUnusable", true,
            "onSuspiciousEmpty", true,
            "onNumericAmbiguity", true
        ));

        return new DocumentProcessingPlan(
            parser, ocr, layout, table, clauseChain, reqProfile, knowledgeProfile,
            budget, retry, review);
    }

    private static String ocrProviderFor(String ocrNeed) {
        return switch (ocrNeed) {
            case "REQUIRED" -> "PRIMARY_OCR";
            case "RECOMMENDED" -> "AUTO_OCR";
            case "NONE" -> "DISABLED";
            default -> "OPTIONAL_OCR";
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }

    private static String firstNonBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private static String stringAt(Map<String, Object> cfg, String key, String fallback) {
        Object value = cfg.get(key);
        return value == null || String.valueOf(value).isBlank()
            ? fallback : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static List<String> listAt(Map<String, Object> cfg, String key, List<String> fallback) {
        Object value = cfg.get(key);
        if (value instanceof List<?> list && !list.isEmpty()) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                out.add(String.valueOf(item));
            }
            return List.copyOf(out);
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapAt(
        Map<String, Object> cfg, String key, Map<String, Object> fallback
    ) {
        Object value = cfg.get(key);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
            return Map.copyOf(out);
        }
        return fallback;
    }
}
