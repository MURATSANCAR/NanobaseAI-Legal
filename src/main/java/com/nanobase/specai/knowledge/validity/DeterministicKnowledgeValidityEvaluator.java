package com.nanobase.specai.knowledge.validity;

import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class DeterministicKnowledgeValidityEvaluator implements KnowledgeValidityEvaluator {
    @Override
    public KnowledgeValidityStatus evaluate(KnowledgeValidityInput input) {
        if (input == null) {
            return KnowledgeValidityStatus.UNKNOWN;
        }
        if (input.datesConflict()) {
            return KnowledgeValidityStatus.CONFLICTING;
        }
        LocalDate evaluation = input.evaluationDate() == null
            ? LocalDate.now() : input.evaluationDate();
        LocalDate issue = input.issueDate();
        LocalDate expiry = input.expiryDate();
        if (issue == null && expiry == null) {
            return KnowledgeValidityStatus.UNKNOWN;
        }
        if (issue != null && evaluation.isBefore(issue)) {
            return KnowledgeValidityStatus.NOT_YET_VALID;
        }
        if (expiry != null && evaluation.isAfter(expiry)) {
            return KnowledgeValidityStatus.EXPIRED;
        }
        if (expiry == null && issue != null) {
            // Issued but no expiry found — not invented as perpetual.
            return KnowledgeValidityStatus.UNKNOWN;
        }
        return KnowledgeValidityStatus.ACTIVE;
    }
}
