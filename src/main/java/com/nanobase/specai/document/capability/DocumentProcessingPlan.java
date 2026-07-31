package com.nanobase.specai.document.capability;

import java.util.List;
import java.util.Map;

/**
 * Policy-neutral processing plan produced from a capability profile.
 * Avoids hardcoded per-format business branches in domain services.
 */
public record DocumentProcessingPlan(
    String parserProviderCode,
    String ocrProviderCode,
    String layoutProviderCode,
    String tableProviderCode,
    List<String> clauseProviderChain,
    String requirementExtractionProfileCode,
    String knowledgeExtractionProfileCode,
    Map<String, Object> resourceBudget,
    Map<String, Object> retryPolicy,
    Map<String, Object> manualReviewPolicy
) {
}
