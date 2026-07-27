package com.nanobase.specai.tender.domain;

import java.time.LocalDate;

public final class TenderDates {
    private TenderDates() {
    }

    public static void validate(LocalDate bidDeadline, LocalDate clarificationDeadline) {
        if (bidDeadline != null && clarificationDeadline != null
            && clarificationDeadline.isAfter(bidDeadline)) {
            throw new IllegalArgumentException(
                "Clarification deadline cannot be after bid deadline");
        }
    }
}
