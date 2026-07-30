package com.nanobase.specai.analysis.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.analysis.domain.ConsequenceType;
import com.nanobase.specai.analysis.domain.EvaluationMethod;
import com.nanobase.specai.analysis.domain.LifecycleStage;
import com.nanobase.specai.analysis.domain.ObligationLevel;
import com.nanobase.specai.analysis.domain.Remediability;
import com.nanobase.specai.analysis.domain.Requirement;
import com.nanobase.specai.analysis.domain.RequirementCriticality;
import com.nanobase.specai.analysis.domain.RequirementType;
import com.nanobase.specai.compliance.application.ClosedWorldValidator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Validates and applies LLM-proposed requirement classification. Invalid enums become UNKNOWN.
 */
@Service
public class RequirementClassificationValidator {

    public enum Status {
        SUCCEEDED,
        FAILED,
        REVIEW_REQUIRED
    }

    public record ClassificationProposal(
        String requirementType,
        String obligationLevel,
        String lifecycleStage,
        String criticality,
        String evaluationMethod,
        String consequenceType,
        String remediability,
        String responsibleDepartment,
        String deadlineType,
        LocalDate explicitDeadline,
        Integer relativeDeadlineDays,
        BigDecimal scoringWeight,
        BigDecimal minimumScore,
        Boolean closedWorldRequired,
        Boolean requiresClarification
    ) {
        public static ClassificationProposal fromCandidate(JsonNode candidate) {
            JsonNode classification = candidate.path("classification");
            JsonNode source = classification.isMissingNode() || classification.isNull()
                ? candidate : classification;
            return new ClassificationProposal(
                text(source, "requirementType"),
                text(source, "obligationLevel"),
                text(source, "lifecycleStage"),
                text(source, "criticality"),
                text(source, "evaluationMethod"),
                text(source, "consequenceType"),
                text(source, "remediability"),
                text(source, "responsibleDepartment"),
                text(source, "deadlineType"),
                date(source, "explicitDeadline"),
                integer(source, "relativeDeadlineDays"),
                decimal(source, "scoringWeight"),
                decimal(source, "minimumScore"),
                bool(source, "closedWorldRequired"),
                bool(source, "requiresClarification"));
        }

        private static String text(JsonNode node, String field) {
            JsonNode value = node.path(field);
            return value.isMissingNode() || value.isNull() ? null : value.asText(null);
        }

        private static Boolean bool(JsonNode node, String field) {
            JsonNode value = node.path(field);
            return value.isMissingNode() || value.isNull() ? null : value.asBoolean();
        }

        private static Integer integer(JsonNode node, String field) {
            JsonNode value = node.path(field);
            return value.isMissingNode() || value.isNull() || !value.isNumber()
                ? null : value.asInt();
        }

        private static BigDecimal decimal(JsonNode node, String field) {
            JsonNode value = node.path(field);
            if (value.isMissingNode() || value.isNull()) {
                return null;
            }
            try {
                return new BigDecimal(value.asText());
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        private static LocalDate date(JsonNode node, String field) {
            String value = text(node, field);
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return LocalDate.parse(value);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    public record Result(
        Status status,
        RequirementType requirementType,
        ObligationLevel obligationLevel,
        LifecycleStage lifecycleStage,
        RequirementCriticality criticality,
        EvaluationMethod evaluationMethod,
        ConsequenceType consequenceType,
        Remediability remediability,
        String responsibleDepartment,
        String deadlineType,
        LocalDate explicitDeadline,
        Integer relativeDeadlineDays,
        BigDecimal scoringWeight,
        BigDecimal minimumScore,
        boolean closedWorldRequired,
        boolean requiresClarification,
        Set<String> reviewReasons
    ) {
    }

    private final ClosedWorldValidator closedWorldValidator;

    public RequirementClassificationValidator(ClosedWorldValidator closedWorldValidator) {
        this.closedWorldValidator = closedWorldValidator;
    }

    public Result validate(ClassificationProposal proposal, java.util.UUID organizationId,
                           java.util.UUID projectId) {
        try {
            RequirementType type = enumOr(proposal.requirementType(), RequirementType.class,
                RequirementType.OTHER);
            ObligationLevel obligation = enumOr(proposal.obligationLevel(), ObligationLevel.class,
                ObligationLevel.UNKNOWN);
            LifecycleStage lifecycle = enumOr(proposal.lifecycleStage(), LifecycleStage.class,
                LifecycleStage.UNKNOWN);
            RequirementCriticality criticality = enumOr(proposal.criticality(),
                RequirementCriticality.class, RequirementCriticality.UNKNOWN);
            EvaluationMethod method = enumOr(proposal.evaluationMethod(), EvaluationMethod.class,
                EvaluationMethod.MANUAL_REVIEW);
            ConsequenceType consequence = enumOr(proposal.consequenceType(), ConsequenceType.class,
                ConsequenceType.UNKNOWN);
            Remediability remediability = enumOr(proposal.remediability(), Remediability.class,
                Remediability.UNKNOWN);
            boolean closedWorldRequested = Boolean.TRUE.equals(proposal.closedWorldRequired());
            boolean closedWorldAllowed = closedWorldRequested
                && closedWorldValidator.hasActiveDeclaration(organizationId, projectId, null);
            boolean requiresClarification = Boolean.TRUE.equals(proposal.requiresClarification());

            java.util.LinkedHashSet<String> reviewReasons = new java.util.LinkedHashSet<>();
            if (obligation == ObligationLevel.PREFERRED
                && consequence == ConsequenceType.DISQUALIFICATION) {
                reviewReasons.add("PREFERRED_WITH_DISQUALIFICATION");
            }
            if (lifecycle == LifecycleStage.POST_AWARD
                && consequence == ConsequenceType.BID_REJECTION) {
                reviewReasons.add("POST_AWARD_WITH_BID_REJECTION");
            }
            if (closedWorldRequested && !closedWorldAllowed) {
                reviewReasons.add("CLOSED_WORLD_NOT_DECLARED");
            }
            Status status = reviewReasons.isEmpty() ? Status.SUCCEEDED : Status.REVIEW_REQUIRED;
            return new Result(status, type, obligation, lifecycle, criticality, method, consequence,
                remediability, proposal.responsibleDepartment(), proposal.deadlineType(),
                proposal.explicitDeadline(), proposal.relativeDeadlineDays(),
                proposal.scoringWeight(), proposal.minimumScore(),
                closedWorldAllowed, requiresClarification, Set.copyOf(reviewReasons));
        } catch (RuntimeException ex) {
            return failed();
        }
    }

    public void apply(Requirement requirement, Result result, Instant now) {
        requirement.classify(result.requirementType(), result.obligationLevel(),
            result.lifecycleStage(), result.criticality(), result.evaluationMethod(),
            result.consequenceType(), result.remediability(), result.responsibleDepartment(),
            result.deadlineType(), result.explicitDeadline(), result.relativeDeadlineDays(),
            result.scoringWeight(), result.minimumScore(), result.closedWorldRequired(),
            result.requiresClarification(), now);
        requirement.classificationStatus(result.status().name());
    }

    public Result failed() {
        return new Result(Status.FAILED, RequirementType.OTHER, ObligationLevel.UNKNOWN,
            LifecycleStage.UNKNOWN, RequirementCriticality.UNKNOWN,
            EvaluationMethod.MANUAL_REVIEW, ConsequenceType.UNKNOWN, Remediability.UNKNOWN,
            null, null, null, null, null, null, false, false, Set.of("CLASSIFICATION_FAILED"));
    }

    private static <E extends Enum<E>> E enumOr(String raw, Class<E> type, E fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            if ("UNKNOWN".equalsIgnoreCase(raw) && fallback.name().equals("UNKNOWN")) {
                return fallback;
            }
            // Prefer UNKNOWN when available on the enum.
            try {
                return Enum.valueOf(type, "UNKNOWN");
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }
}
