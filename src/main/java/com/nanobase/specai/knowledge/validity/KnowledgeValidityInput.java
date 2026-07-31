package com.nanobase.specai.knowledge.validity;

import java.time.LocalDate;

public record KnowledgeValidityInput(
    LocalDate issueDate,
    LocalDate expiryDate,
    LocalDate evaluationDate,
    boolean datesConflict
) {
}
