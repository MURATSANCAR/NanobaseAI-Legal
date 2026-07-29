package com.nanobase.specai.compliance.application;

/**
 * Task-level routing decision for FAST vs BALANCED compliance workloads.
 *
 * <p>First production rollout uses {@link Route#FAST_PRECHECK_BALANCED_FINAL} whenever
 * candidates exist. {@link Route#FAST_ONLY} is reserved for a later LIVE_FAST gate.
 */
public final class ComplianceModelRoutingPolicy {

    public enum Route {
        NO_LLM_REQUIRED,
        FAST_PRECHECK_BALANCED_FINAL,
        FAST_ONLY,
        BALANCED_ONLY,
        BALANCED_ESCALATION
    }

    public enum MatchType {
        EXPLICIT_SUPPORT,
        EXPLICIT_CONTRADICTION,
        PARTIAL_MATCH,
        TOPIC_ONLY,
        NO_MATCH;

        public static MatchType from(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                return MatchType.valueOf(raw.trim());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    public record RoutingInput(
        String requirementId,
        int candidateCount,
        String requirementCriticality,
        boolean hasConflictingCandidates,
        boolean closedWorldRequired,
        Double fastConfidence,
        MatchType fastMatchType
    ) {
    }

    private final double fastRelevanceThreshold;
    private final double fastFinalDecisionThreshold;
    private final boolean liveFastEnabled;

    public ComplianceModelRoutingPolicy(
        double fastRelevanceThreshold,
        double fastFinalDecisionThreshold,
        boolean liveFastEnabled
    ) {
        this.fastRelevanceThreshold = fastRelevanceThreshold;
        this.fastFinalDecisionThreshold = fastFinalDecisionThreshold;
        this.liveFastEnabled = liveFastEnabled;
    }

    public static ComplianceModelRoutingPolicy defaults() {
        return new ComplianceModelRoutingPolicy(0.80, 0.95, false);
    }

    public Route route(RoutingInput input) {
        if (input == null || input.candidateCount() <= 0) {
            return Route.NO_LLM_REQUIRED;
        }
        if (isHighCriticality(input.requirementCriticality())
            || input.hasConflictingCandidates()
            || input.closedWorldRequired()) {
            return Route.BALANCED_ONLY;
        }
        MatchType matchType = input.fastMatchType();
        if (matchType == MatchType.TOPIC_ONLY || matchType == MatchType.NO_MATCH) {
            return Route.BALANCED_ESCALATION;
        }
        Double confidence = input.fastConfidence();
        if (confidence != null && confidence < fastRelevanceThreshold) {
            return Route.BALANCED_ESCALATION;
        }
        if (liveFastEnabled && qualifiesForFastOnly(input)) {
            return Route.FAST_ONLY;
        }
        return Route.FAST_PRECHECK_BALANCED_FINAL;
    }

    public boolean qualifiesForFastOnly(RoutingInput input) {
        if (input == null || input.candidateCount() <= 0 || input.candidateCount() > 2) {
            return false;
        }
        if (isHighCriticality(input.requirementCriticality())
            || input.hasConflictingCandidates()
            || input.closedWorldRequired()) {
            return false;
        }
        MatchType matchType = input.fastMatchType();
        if (matchType != MatchType.EXPLICIT_SUPPORT
            && matchType != MatchType.EXPLICIT_CONTRADICTION) {
            return false;
        }
        Double confidence = input.fastConfidence();
        return confidence != null && confidence >= fastFinalDecisionThreshold;
    }

    public double fastRelevanceThreshold() {
        return fastRelevanceThreshold;
    }

    public double fastFinalDecisionThreshold() {
        return fastFinalDecisionThreshold;
    }

    private static boolean isHighCriticality(String criticality) {
        return criticality != null && "HIGH".equalsIgnoreCase(criticality.trim());
    }
}
