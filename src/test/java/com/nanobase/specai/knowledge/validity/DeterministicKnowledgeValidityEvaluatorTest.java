package com.nanobase.specai.knowledge.validity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DeterministicKnowledgeValidityEvaluatorTest {

    private final DeterministicKnowledgeValidityEvaluator evaluator =
        new DeterministicKnowledgeValidityEvaluator();

    @Test
    void activeWhenWithinIssueAndExpiry() {
        assertThat(evaluator.evaluate(new KnowledgeValidityInput(
            LocalDate.of(2024, 1, 1), LocalDate.of(2027, 1, 1),
            LocalDate.of(2026, 7, 31), false)))
            .isEqualTo(KnowledgeValidityStatus.ACTIVE);
    }

    @Test
    void expiredAfterExpiryDate() {
        assertThat(evaluator.evaluate(new KnowledgeValidityInput(
            LocalDate.of(2020, 1, 1), LocalDate.of(2024, 1, 1),
            LocalDate.of(2026, 7, 31), false)))
            .isEqualTo(KnowledgeValidityStatus.EXPIRED);
    }

    @Test
    void unknownWhenExpiryMissing() {
        assertThat(evaluator.evaluate(new KnowledgeValidityInput(
            LocalDate.of(2024, 1, 1), null, LocalDate.of(2026, 7, 31), false)))
            .isEqualTo(KnowledgeValidityStatus.UNKNOWN);
    }

    @Test
    void conflictingWhenFlagged() {
        assertThat(evaluator.evaluate(new KnowledgeValidityInput(
            LocalDate.of(2024, 1, 1), LocalDate.of(2027, 1, 1),
            LocalDate.of(2026, 7, 31), true)))
            .isEqualTo(KnowledgeValidityStatus.CONFLICTING);
    }
}
