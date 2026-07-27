package com.nanobase.specai.tender.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TenderDatesTest {
    @Test
    void acceptsClarificationBeforeBidDeadline() {
        assertThatCode(() -> TenderDates.validate(
            LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 15)))
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsClarificationAfterBidDeadline() {
        assertThatThrownBy(() -> TenderDates.validate(
            LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 30)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
